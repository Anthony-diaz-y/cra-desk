package app

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

const goodKey = "crd_goodgood_secretvalue123"

type fakeAPI struct {
	mu        sync.Mutex
	statuses  []string
	scanError string
	kev       bool
	calls     int
}

// handler imita los endpoints de CRA Desk que usa la CLI.
func (f *fakeAPI) handler(t *testing.T) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+goodKey {
			problem(w, 401, "Unauthorized", "Invalid API key", nil)
			return
		}
		switch {
		case r.URL.Path == "/api/auth/me":
			writeJSON(w, 200, map[string]any{"organizationId": "o1", "organizationName": "Acme", "role": "CLI"})
		case r.URL.Path == "/api/sbom" && r.Method == http.MethodPut:
			switch r.URL.Query().Get("product") {
			case "Missing":
				problem(w, 404, "Not found", "Product 'Missing' not found", nil)
			case "Bad":
				problem(w, 400, "Invalid request", "Not a valid CycloneDX JSON SBOM",
					map[string]string{"bomFormat": "must be CycloneDX"})
			default:
				writeJSON(w, 201, map[string]any{"id": "s1", "productId": "p1", "versionId": "v1",
					"specVersion": "1.5", "componentCount": 5, "componentsWithoutPurl": 1})
			}
		case r.URL.Path == "/api/products/p1/versions/v1/vulnerabilities":
			f.mu.Lock()
			status := f.statuses[min(f.calls, len(f.statuses)-1)]
			f.calls++
			f.mu.Unlock()
			items := []map[string]any{}
			if status == "DONE" {
				items = append(items, map[string]any{"id": "GHSA-35jh-r3h4-6jhm", "aliases": []string{"CVE-2021-23337"},
					"severity": "HIGH", "kev": false, "componentName": "lodash", "componentVersion": "4.17.15"})
				if f.kev {
					items = append(items, map[string]any{"id": "GHSA-jfh8-c2jp-5v3q", "aliases": []string{"CVE-2021-44228"},
						"severity": "CRITICAL", "kev": true, "componentName": "log4j-core", "componentVersion": "2.14.1"})
				}
			}
			writeJSON(w, 200, map[string]any{"status": status, "error": f.scanError, "items": items})
		default:
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
			w.WriteHeader(404)
		}
	}
}

// writeJSON responde con el estado y el cuerpo JSON indicados.
func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

// problem responde con un error RFC 9457.
func problem(w http.ResponseWriter, status int, title, detail string, errs map[string]string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]any{"status": status, "title": title, "detail": detail, "errors": errs})
}

type result struct {
	code   int
	stdout string
	stderr string
}

// run ejecuta la CLI con esos argumentos, entrada y variables de entorno, y devuelve código y salidas.
func run(t *testing.T, configPath, stdin string, env map[string]string, args ...string) result {
	t.Helper()
	var out, errOut bytes.Buffer
	code := Run(Env{
		Args:         args,
		Stdin:        strings.NewReader(stdin),
		Stdout:       &out,
		Stderr:       &errOut,
		Getenv:       func(k string) string { return env[k] },
		ConfigPath:   configPath,
		Version:      "1.2.3",
		PollInterval: 5 * time.Millisecond,
	})
	return result{code, out.String(), errOut.String()}
}

// setup arranca la API falsa y crea un archivo bom.json; devuelve la URL, el archivo y la ruta de configuración.
func setup(t *testing.T, api *fakeAPI) (string, string, string) {
	t.Helper()
	server := httptest.NewServer(api.handler(t))
	t.Cleanup(server.Close)
	dir := t.TempDir()
	bom := filepath.Join(dir, "bom.json")
	if err := os.WriteFile(bom, []byte(`{"bomFormat":"CycloneDX","specVersion":"1.5"}`), 0o644); err != nil {
		t.Fatal(err)
	}
	return server.URL, bom, filepath.Join(dir, "config", "config.json")
}

// ciEnv arma las variables de entorno que usaría un pipeline de CI.
func ciEnv(url string) map[string]string {
	return map[string]string{"CRADESK_API_URL": url, "CRADESK_API_KEY": goodKey}
}

// TestLoginStoresConfigAndNeverPrintsKey comprueba que login valida la clave, la guarda y no la muestra.
func TestLoginStoresConfigAndNeverPrintsKey(t *testing.T) {
	url, _, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, goodKey+"\n", nil, "login", "--api-url", url)

	if r.code != ExitOK {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
	if !strings.Contains(r.stdout, "Logged in to Acme ("+url+")") {
		t.Fatalf("unexpected stdout %q", r.stdout)
	}
	data, err := os.ReadFile(configPath)
	if err != nil || !strings.Contains(string(data), goodKey) {
		t.Fatalf("config not saved: %v %s", err, data)
	}
	if strings.Contains(r.stdout+r.stderr, goodKey) {
		t.Fatal("the API key was printed")
	}
}

// TestLoginWithInvalidKeyExits2 comprueba que una clave rechazada por la API da código 2 y no se guarda.
func TestLoginWithInvalidKeyExits2(t *testing.T) {
	url, _, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "crd_wrongkey_value\n", nil, "login", "--api-url", url)

	if r.code != ExitAPI || !strings.Contains(r.stderr, "401 Unauthorized") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
	if _, err := os.Stat(configPath); err == nil {
		t.Fatal("config saved with an invalid key")
	}
}

// TestUploadPrintsSummary comprueba que la subida muestra el resumen del SBOM.
func TestUploadPrintsSummary(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0", "--file", bom)

	if r.code != ExitOK {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
	if !strings.Contains(r.stdout, "Uploaded SBOM for Smart Lock 1.0.0: 5 components (1 without purl)") {
		t.Fatalf("unexpected stdout %q", r.stdout)
	}
}

// TestUploadMissingFileExits1 comprueba que un archivo inexistente da código 1 con un mensaje claro.
func TestUploadMissingFileExits1(t *testing.T) {
	url, _, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0",
		"--file", filepath.Join(t.TempDir(), "nope.json"))

	if r.code != ExitUsage || !strings.Contains(r.stderr, "cannot open SBOM file") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestUploadWithoutConfigExits1 comprueba que sin login ni variables de entorno se explica qué falta.
func TestUploadWithoutConfigExits1(t *testing.T) {
	_, bom, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", nil, "upload", "--product", "Smart Lock", "--version", "1.0.0", "--file", bom)

	if r.code != ExitUsage || !strings.Contains(r.stderr, "API URL is not set") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestUploadRequiresProductAndVersion comprueba que faltan flags obligatorios da código 1.
func TestUploadRequiresProductAndVersion(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", ciEnv(url), "upload", "--file", bom)

	if r.code != ExitUsage || !strings.Contains(r.stderr, "--product and --version are required") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestUploadRejectsPositionalArguments comprueba que un archivo pasado sin --file se rechaza en vez de subir bom.json.
func TestUploadRejectsPositionalArguments(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0", bom)

	if r.code != ExitUsage || !strings.Contains(r.stderr, "unexpected argument") || !strings.Contains(r.stderr, "--file") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestUploadAPIErrorExits2WithFieldErrors comprueba que un SBOM rechazado da código 2 y muestra los errores por campo.
func TestUploadAPIErrorExits2WithFieldErrors(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Bad", "--version", "1.0.0", "--file", bom)

	if r.code != ExitAPI || !strings.Contains(r.stderr, "bomFormat: must be CycloneDX") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestWaitAndFailOnKevExits3 comprueba que con --fail-on-kev una vulnerabilidad KEV corta con código 3.
func TestWaitAndFailOnKevExits3(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{statuses: []string{"PENDING", "RUNNING", "DONE"}, kev: true})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0",
		"--file", bom, "--fail-on-kev")

	if r.code != ExitKEV {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
	if !strings.Contains(r.stdout, "CVE-2021-44228") || !strings.Contains(r.stdout, "2 vulnerabilities, 1 actively exploited (KEV)") {
		t.Fatalf("unexpected stdout %q", r.stdout)
	}
}

// TestWaitWithoutKevExits0 comprueba que esperar sin vulnerabilidades KEV termina con código 0.
func TestWaitWithoutKevExits0(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{statuses: []string{"PENDING", "DONE"}})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0",
		"--file", bom, "--wait", "--fail-on-kev")

	if r.code != ExitOK || !strings.Contains(r.stdout, "1 vulnerabilities, 0 actively exploited (KEV)") {
		t.Fatalf("exit %d, stdout %q, stderr %q", r.code, r.stdout, r.stderr)
	}
}

// TestScanFailedExits4 comprueba que un escaneo fallido da código 4 con el motivo.
func TestScanFailedExits4(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{statuses: []string{"FAILED"}, scanError: "OSV: HTTP 503"})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0",
		"--file", bom, "--wait")

	if r.code != ExitScan || !strings.Contains(r.stderr, "OSV: HTTP 503") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestWaitTimeoutExits4 comprueba que si el escaneo no termina a tiempo se sale con código 4.
func TestWaitTimeoutExits4(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{statuses: []string{"PENDING"}})

	r := run(t, configPath, "", ciEnv(url), "upload", "--product", "Smart Lock", "--version", "1.0.0",
		"--file", bom, "--wait", "--timeout", "50ms")

	if r.code != ExitScan || !strings.Contains(r.stderr, "timed out") {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestSavedLoginIsUsedByUpload comprueba que después de login la subida funciona sin variables de entorno.
func TestSavedLoginIsUsedByUpload(t *testing.T) {
	url, bom, configPath := setup(t, &fakeAPI{})
	run(t, configPath, goodKey+"\n", nil, "login", "--api-url", url)

	r := run(t, configPath, "", nil, "upload", "--product", "Smart Lock", "--version", "1.0.0", "--file", bom)

	if r.code != ExitOK {
		t.Fatalf("exit %d, stderr %q", r.code, r.stderr)
	}
}

// TestUnknownCommandShowsUsage comprueba que un comando desconocido o ninguno muestra el uso con código 1.
func TestUnknownCommandShowsUsage(t *testing.T) {
	for _, args := range [][]string{{"frobnicate"}, {}} {
		r := run(t, "", "", nil, args...)
		if r.code != ExitUsage || !strings.Contains(r.stderr, "Usage:") {
			t.Fatalf("args %v: exit %d, stderr %q", args, r.code, r.stderr)
		}
	}
}

// TestVersionPrintsVersion comprueba que version muestra la versión del binario.
func TestVersionPrintsVersion(t *testing.T) {
	r := run(t, "", "", nil, "version")

	if r.code != ExitOK || strings.TrimSpace(r.stdout) != "cradesk 1.2.3" {
		t.Fatalf("exit %d, stdout %q", r.code, r.stdout)
	}
}
