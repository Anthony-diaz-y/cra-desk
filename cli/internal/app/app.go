package app

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"strings"
	"text/tabwriter"
	"time"

	"github.com/Anthony-diaz-y/cra-desk/cli/internal/api"
	"github.com/Anthony-diaz-y/cra-desk/cli/internal/config"
)

const (
	ExitOK    = 0
	ExitUsage = 1
	ExitAPI   = 2
	ExitKEV   = 3
	ExitScan  = 4
)

const (
	defaultPollInterval = 5 * time.Second
	maxRows             = 25
)

const usageText = `Usage:
  cradesk login --api-url URL           read the API key from stdin, check it and save it
  cradesk upload --product NAME --version VERSION [flags]
                                        upload a CycloneDX JSON SBOM for that product version
  cradesk version                       print the version

Upload flags:
  --file PATH            SBOM file (default bom.json)
  --no-create-version    fail if the version does not exist yet
  --wait                 wait for the vulnerability scan and print the results
  --timeout DURATION     how long to wait for the scan (default 5m)
  --fail-on-kev          exit 3 if an actively exploited (CISA KEV) vulnerability is found; implies --wait

Environment: CRADESK_API_URL and CRADESK_API_KEY override the saved login.
Exit codes: 0 ok, 1 usage or input error, 2 API or network error, 3 KEV found, 4 scan failed or timed out.
`

type Env struct {
	Args         []string
	Stdin        io.Reader
	Stdout       io.Writer
	Stderr       io.Writer
	Getenv       func(string) string
	ConfigPath   string
	Version      string
	PollInterval time.Duration
}

// Run ejecuta el subcomando pedido y devuelve el código de salida del proceso.
func Run(env Env) int {
	if len(env.Args) == 0 {
		fmt.Fprint(env.Stderr, usageText)
		return ExitUsage
	}
	switch env.Args[0] {
	case "login":
		return login(env, env.Args[1:])
	case "upload":
		return upload(env, env.Args[1:])
	case "version":
		fmt.Fprintf(env.Stdout, "cradesk %s\n", env.Version)
		return ExitOK
	case "help", "-h", "--help":
		fmt.Fprint(env.Stderr, usageText)
		return ExitUsage
	default:
		fmt.Fprintf(env.Stderr, "unknown command %q\n\n%s", env.Args[0], usageText)
		return ExitUsage
	}
}

// login lee la clave de la entrada estándar, la valida contra la API y guarda la configuración.
func login(env Env, args []string) int {
	flags := newFlagSet("login", env.Stderr)
	apiURL := flags.String("api-url", "", "CRA Desk URL, for example https://cradesk.example.com")
	if flags.Parse(args) != nil {
		return ExitUsage
	}
	fmt.Fprint(env.Stderr, "API key: ")
	scanner := bufio.NewScanner(env.Stdin)
	scanner.Scan()
	fmt.Fprintln(env.Stderr)
	cfg := config.Config{APIURL: strings.TrimRight(strings.TrimSpace(*apiURL), "/"), APIKey: strings.TrimSpace(scanner.Text())}
	if err := cfg.Validate(); err != nil {
		return fail(env, ExitUsage, err)
	}
	me, err := newClient(env, cfg).Me(context.Background())
	if err != nil {
		return fail(env, ExitAPI, err)
	}
	path, err := configPath(env)
	if err == nil {
		err = config.Save(path, cfg)
	}
	if err != nil {
		return fail(env, ExitUsage, err)
	}
	fmt.Fprintf(env.Stdout, "Logged in to %s (%s)\n", me.OrganizationName, cfg.APIURL)
	return ExitOK
}

// upload sube el SBOM y, si se pide, espera el escaneo y decide el código de salida según los KEV.
func upload(env Env, args []string) int {
	flags := newFlagSet("upload", env.Stderr)
	product := flags.String("product", "", "product name in CRA Desk")
	version := flags.String("version", "", "product version, for example the release tag")
	file := flags.String("file", "bom.json", "CycloneDX JSON SBOM file")
	noCreate := flags.Bool("no-create-version", false, "fail if the version does not exist")
	wait := flags.Bool("wait", false, "wait for the vulnerability scan")
	timeout := flags.Duration("timeout", 5*time.Minute, "maximum time to wait for the scan")
	failOnKev := flags.Bool("fail-on-kev", false, "exit 3 if a CISA KEV vulnerability is found")
	if flags.Parse(args) != nil {
		return ExitUsage
	}
	if strings.TrimSpace(*product) == "" || strings.TrimSpace(*version) == "" {
		return fail(env, ExitUsage, errors.New("--product and --version are required"))
	}
	cfg, code := loadConfig(env)
	if code != ExitOK {
		return code
	}
	sbom, err := os.Open(*file)
	if err != nil {
		return fail(env, ExitUsage, fmt.Errorf("cannot open SBOM file %s: %v", *file, err))
	}
	defer sbom.Close()
	client := newClient(env, cfg)
	summary, replaced, err := client.UploadSBOM(context.Background(), *product, *version, !*noCreate, sbom)
	if err != nil {
		return fail(env, ExitAPI, err)
	}
	fmt.Fprintf(env.Stdout, "Uploaded SBOM for %s %s: %d components (%d without purl)", *product, *version,
		summary.ComponentCount, summary.ComponentsWithoutPurl)
	if replaced {
		fmt.Fprint(env.Stdout, " (replaced the previous SBOM)")
	}
	fmt.Fprintln(env.Stdout)
	if !*wait && !*failOnKev {
		return ExitOK
	}
	return waitForScan(env, client, summary, *timeout, *failOnKev)
}

// waitForScan consulta el escaneo hasta que termina, falla o se acaba el tiempo, y muestra el resultado.
func waitForScan(env Env, client *api.Client, summary api.SbomSummary, timeout time.Duration, failOnKev bool) int {
	interval := env.PollInterval
	if interval <= 0 {
		interval = defaultPollInterval
	}
	fmt.Fprintln(env.Stdout, "Waiting for the vulnerability scan (OSV + CISA KEV)...")
	deadline := time.Now().Add(timeout)
	for {
		result, err := client.VersionVulnerabilities(context.Background(), summary.ProductID, summary.VersionID)
		if err != nil {
			return fail(env, ExitAPI, err)
		}
		switch result.Status {
		case "DONE":
			return report(env, result, failOnKev)
		case "FAILED":
			return fail(env, ExitScan, fmt.Errorf("vulnerability scan failed: %s", result.Error))
		}
		if time.Now().After(deadline) {
			return fail(env, ExitScan, fmt.Errorf("timed out after %s waiting for the vulnerability scan", timeout))
		}
		time.Sleep(interval)
	}
}

// report imprime la tabla de vulnerabilidades y devuelve 3 si hay KEV y se pidió --fail-on-kev.
func report(env Env, result api.Vulnerabilities, failOnKev bool) int {
	kev := 0
	for _, v := range result.Items {
		if v.Kev {
			kev++
		}
	}
	if len(result.Items) > 0 {
		table := tabwriter.NewWriter(env.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(table, "KEV\tID\tCVE\tSEVERITY\tCOMPONENT")
		for i, v := range result.Items {
			if i == maxRows {
				fmt.Fprintf(table, "...\tand %d more\t\t\t\n", len(result.Items)-maxRows)
				break
			}
			mark := ""
			if v.Kev {
				mark = "YES"
			}
			fmt.Fprintf(table, "%s\t%s\t%s\t%s\t%s %s\n", mark, v.ID, firstCVE(v.Aliases), v.Severity,
				v.ComponentName, v.ComponentVersion)
		}
		table.Flush()
	}
	fmt.Fprintf(env.Stdout, "%d vulnerabilities, %d actively exploited (KEV)\n", len(result.Items), kev)
	if failOnKev && kev > 0 {
		return fail(env, ExitKEV, errors.New("actively exploited vulnerabilities found (CISA KEV)"))
	}
	return ExitOK
}

// firstCVE devuelve el primer alias CVE de una vulnerabilidad, o un guion si no tiene.
func firstCVE(aliases []string) string {
	for _, alias := range aliases {
		if strings.HasPrefix(strings.ToUpper(alias), "CVE-") {
			return alias
		}
	}
	return "-"
}

// loadConfig lee la configuración guardada y las variables de entorno, y exige que esté completa.
func loadConfig(env Env) (config.Config, int) {
	path, err := configPath(env)
	if err != nil {
		return config.Config{}, fail(env, ExitUsage, err)
	}
	cfg, err := config.Load(path, env.Getenv)
	if err == nil {
		err = cfg.Validate()
	}
	if err != nil {
		return config.Config{}, fail(env, ExitUsage, err)
	}
	return cfg, ExitOK
}

// configPath devuelve la ruta de configuración indicada o la ruta por defecto del usuario.
func configPath(env Env) (string, error) {
	if env.ConfigPath != "" {
		return env.ConfigPath, nil
	}
	return config.DefaultPath()
}

// newClient crea el cliente de la API con el User-Agent de esta versión.
func newClient(env Env, cfg config.Config) *api.Client {
	return api.New(cfg.APIURL, cfg.APIKey, "cradesk/"+env.Version)
}

// newFlagSet crea el conjunto de flags de un subcomando que escribe sus errores en la salida de error.
func newFlagSet(name string, stderr io.Writer) *flag.FlagSet {
	flags := flag.NewFlagSet(name, flag.ContinueOnError)
	flags.SetOutput(stderr)
	return flags
}

// fail escribe el error en la salida de error y devuelve el código indicado.
func fail(env Env, code int, err error) int {
	fmt.Fprintf(env.Stderr, "error: %v\n", err)
	return code
}
