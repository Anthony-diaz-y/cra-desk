# CRA Desk — Etapa 5: CLI en Go y GitHub Action — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Modo de trabajo:** el usuario elige cada día entre **tutor** y **avance directo**. En modo tutor, las tareas **[Alumno]** (el código Go, según el diseño) siguen este ciclo: lección corta de Go con un ejemplo de otro dominio, Claude escribe el test, el alumno escribe el código, Claude revisa con pistas progresivas y el alumno anota 3 líneas en `docs/aprendizaje.md`. Las tareas **[Claude]** las hace Claude y luego las explica. En avance directo, Claude implementa todo con TDD.

**Goal:** Un binario `cradesk` que un fabricante usa en su CI para subir el SBOM de cada versión con una clave de API, esperar el escaneo y, si quiere, cortar el build cuando aparece una vulnerabilidad explotada (KEV). Una GitHub Action lo envuelve en pocas líneas de YAML.

**Architecture:** Módulo Go en `cli/` solo con la biblioteca estándar (`flag`, `net/http`, `encoding/json`, `os`). Paquetes: `cmd/cradesk` (main), `internal/config` (URL y clave), `internal/api` (cliente HTTP) e `internal/app` (comandos y códigos de salida). Del lado Java, un endpoint pensado para la CLI sube el SBOM por **nombre** de producto y versión. La Action es compuesta (`cli/action.yml`): instala Go con `actions/setup-go`, compila la CLI desde el propio repo y la ejecuta.

**Tech Stack:** Go 1.25+ (instalado 1.27.3), biblioteca estándar, `net/http/httptest` para los tests. GitHub Actions (composite). Del lado de la API, lo de siempre.

**Spec:** `docs/superpowers/specs/2026-11-13-cra-desk-design.md` (§2: "Sube el SBOM … con la CLI en Go (también usable como GitHub Action)"; "La CLI y la GitHub Action van en el mismo monorepo y se publican con GitHub Releases (`cli/action.yml`)"; §5 Tutor: "La CLI en Go" la escribe el alumno; §6 etapa 5: `cradesk login/upload`, GitHub Action, binarios multiplataforma).

## Global Constraints

- Código, flags, mensajes de la CLI y README en inglés. Explicaciones y `docs/aprendizaje.md` en español.
- **Comentarios:** solo un comentario de una línea en español encima de cada función o método (Go y Java, también tests). Ningún otro comentario en código, YAML ni scripts.
- Go: solo biblioteca estándar; `gofmt` y `go vet` limpios; tests con `go test ./...` desde `cli/`.
- La clave de API **nunca** se imprime ni se escribe en logs; en el archivo de configuración se guarda con permisos `0600`.
- Errores de la API (RFC 9457) se muestran como una línea legible, más los `errors` por campo si vienen; sin trazas.
- Maven wrapper 3.9.16 para la parte Java. Crear tags, releases o hacer push solo con confirmación del usuario.

## Decisiones de diseño de esta etapa

| Tema | Decisión | Por qué |
|---|---|---|
| Subida por nombre | Nuevo `PUT /api/sbom?product=<nombre>&version=<versión>&createVersion=true` (cuerpo: el JSON CycloneDX crudo, como la Etapa 3). Busca el producto por nombre en la empresa de la clave (sin distinguir mayúsculas ni espacios extra); si no existe → 404 `"Product 'X' not found"` (no se crea solo, para no fabricar productos por un error de tipeo). La versión se crea si falta y `createVersion=true`; si no, 404. Responde el mismo `SbomSummary` (201 nuevo, 200 reemplazo), que trae `productId` y `versionId`. | En CI uno conoce el nombre del producto y el tag de la versión, no sus UUID. Era lo pendiente de la Etapa 3. |
| Carrera al crear la versión | Si dos jobs crean la misma versión a la vez, el segundo recibe `DuplicateVersionException` internamente y vuelve a buscarla; nunca 409 ni 500. | Dos pipelines del mismo tag pasan en la práctica. |
| Sin framework de CLI | `flag` de la biblioteca estándar con subcomandos a mano (`cradesk login`, `upload`, `version`). | Binario chico, sin dependencias que auditar, y más fácil de aprender Go desde cero. |
| Dónde vive la configuración | `os.UserConfigDir()/cradesk/config.json` con `{"apiUrl", "apiKey"}`, permisos `0600`, carpeta `0700`. Las variables `CRADESK_API_URL` y `CRADESK_API_KEY` tienen prioridad (así se usa en CI, sin `login`). | Patrón estándar de CLIs; en CI no hay disco persistente. |
| Cómo se pide la clave | `cradesk login --api-url URL` lee la clave de la entrada estándar (una línea; sirve con `echo $KEY | cradesk login …`), no de un flag. Valida con `GET /api/auth/me` antes de guardar. | Un flag con la clave queda en el historial de la shell y en los logs de CI. |
| Códigos de salida | `0` bien · `1` error de uso o de entrada (archivo, flags) · `2` error de la API o de red · `3` hay hallazgos KEV para esa versión y se pidió `--fail-on-kev` · `4` el escaneo no terminó a tiempo o quedó `FAILED`. | Un CI necesita distinguir "falló la herramienta" de "hay una vulnerabilidad explotada". |
| `upload` | `cradesk upload --product NAME --version V [--file bom.json] [--no-create-version] [--wait] [--timeout 5m] [--fail-on-kev]`. `--file` por defecto `bom.json`. `--fail-on-kev` implica `--wait`. Con `--wait` consulta `GET …/vulnerabilities` cada 5 s hasta `DONE` o `FAILED`. | Lo mínimo para un pipeline real. |
| Red | `http.Client` con timeout de 60 s por petición y el proxy del entorno (`HTTPS_PROXY`), `User-Agent: cradesk/<versión>`. | Muchas CI corporativas salen por proxy. |
| GitHub Action | Composite en `cli/action.yml`: entradas `api-url`, `api-key`, `product`, `version`, `file` (`bom.json`), `fail-on-kev` (`false`), `timeout` (`5m`). Pasos: `actions/setup-go` con `go-version-file: <action>/cli/go.mod`, `go build` desde `$GITHUB_ACTION_PATH`, y `cradesk upload` con la clave por variable de entorno. Uso: `uses: Anthony-diaz-y/cra-desk/cli@main`. | Funciona sin publicar binarios y sin tokens extra. |
| Releases | Workflow `.github/workflows/cli-release.yml` que, al empujar un tag `cli-v*`, compila para linux/darwin/windows × amd64/arm64 y sube los binarios a un GitHub Release. Se crea el workflow; **el primer tag lo crea el usuario** cuando quiera. | Spec §2 (GitHub Releases). Publicar es una acción pública. |
| Versión del binario | Variable `version` en `main`, por defecto `dev`, fijada con `-ldflags "-X main.version=…"` en el release. | Estándar en Go. |

## Review Focus

1. **Secretos:** la clave nunca aparece en la salida, en errores ni en la línea de comandos; el archivo de configuración queda con `0600`; las variables de entorno ganan sobre el archivo (Tareas 2 y 4).
2. **Fallos de red y de la API:** API inaccesible, clave revocada (401), SBOM inválido (400 con `errors`), demasiado grande (413), producto inexistente (404) → mensaje claro de una línea y el código de salida correcto, nunca un `panic` ni una traza (Tareas 3 y 4).
3. **Espera del escaneo:** `--wait` con escaneo `FAILED` o que no termina antes de `--timeout` → código 4; `--fail-on-kev` cuenta solo los KEV de esa versión (Tarea 4).
4. **Nombres y carreras del lado de la API:** `" smart  LOCK "` encuentra "Smart Lock"; dos subidas simultáneas que crean la misma versión → una 201 y otra 200, sin 409 ni 500; otra empresa con el mismo nombre de producto no se mezcla (Tarea 1).
5. **Multiplataforma:** rutas de Windows, archivo inexistente (código 1 con mensaje), binarios para linux/darwin/windows compilan (Tareas 4 y 5).

---

### Task 1 [Claude]: Subida por nombre en la API

**Files:**
- Modify: `api/src/main/java/io/cradesk/api/catalog/ProductService.java` (`resolveVersionByName`), `ProductRepository.java` (búsqueda por nombre normalizado en la empresa)
- Modify: `api/src/main/java/io/cradesk/api/sbom/SbomController.java`, `SbomService.java`
- Test: `api/src/test/java/io/cradesk/api/sbom/SbomUploadByNameApiIT.java`; agregar a `ApiKeyApiIT` `keyCanReadMe`

**Interfaces:**
- Produces:
  - `ProductService.resolveVersionByName(String productName, String version, boolean create)` → `ProductVersion`; normaliza con `Names.normalizeName` y compara con `lower()`; producto inexistente → `ProductNotFoundByNameException` (404 `"Product '<nombre>' not found"`); versión inexistente con `create=false` → `VersionNotFoundException`; con `create=true` la crea (recortada) y, ante `DuplicateVersionException` por carrera, la vuelve a buscar.
  - `PUT /api/sbom?product=…&version=…&createVersion=true|false` (por defecto `true`) → igual que la subida por ids: 201/200 `SbomSummary`, 400, 404, 413, 415. `product` o `version` vacíos → 400 con `errors.product` / `errors.version`.
  - `GET /api/auth/me` con `Bearer` devuelve 200 con `organizationName` y `role` `CLI`.
- [ ] **Step 1:** Escribir `SbomUploadByNameApiIT` (sin transacción) con: `uploadByNameCreatesVersion` (201, la versión `1.4.0` existe después), `nameMatchingIgnoresCaseAndSpaces` (`" smart  LOCK "`), `unknownProductReturns404`, `existingVersionWithoutCreateIsReplaced` (200 en la segunda subida), `missingVersionWithoutCreateReturns404`, `blankParamsReturn400`, `otherTenantsProductIsNotFound`, `concurrentUploadsCreatingSameVersion` (dos hilos con la clave → códigos {201, 200}, 1 versión, 1 SBOM). Y `ApiKeyApiIT.keyCanReadMe`.
- [ ] **Step 2:** `cd api && ./mvnw clean verify` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(sbom): upload sbom by product and version name for the CLI`.

### Task 2 [Alumno]: Módulo Go y configuración

**Lección previa:** qué es un módulo y un paquete en Go, `struct`, métodos, el patrón `valor, err`, `encoding/json`, tests de tabla y `t.TempDir()`.

**Files:**
- Create: `cli/go.mod` (`module github.com/Anthony-diaz-y/cra-desk/cli`, `go 1.25`), `cli/internal/config/config.go`, `cli/internal/config/config_test.go`; borrar `cli/.gitkeep`

**Interfaces:**
- Produces:
  - `type Config struct { APIURL string \`json:"apiUrl"\`; APIKey string \`json:"apiKey"\` }`.
  - `func DefaultPath() (string, error)` → `<UserConfigDir>/cradesk/config.json`.
  - `func Load(path string, getenv func(string) string) (Config, error)`: lee el archivo si existe (si no, `Config{}` sin error) y aplica `CRADESK_API_URL` / `CRADESK_API_KEY` encima; quita la `/` final de la URL.
  - `func Save(path string, c Config) error`: crea la carpeta con `0700` y escribe el archivo con `0600`.
  - `func (c Config) Validate() error`: URL `http`/`https` válida y clave que empieza con `crd_`; errores en inglés (`"API URL is not set (run 'cradesk login' or set CRADESK_API_URL)"`, `"API key is not set (…CRADESK_API_KEY)"`, `"API URL must start with http:// or https://"`).
- [ ] **Step 1 (Claude):** `config_test.go`: `TestLoadMissingFileReturnsEmpty`, `TestEnvOverridesFile`, `TestSaveThenLoadRoundTrip`, `TestSaveUsesPrivatePermissions` (en Unix, `0600`; en Windows se omite con `t.Skip`), `TestValidate` (tabla: sin URL, sin clave, URL sin esquema, clave sin `crd_`, válida), `TestTrailingSlashIsRemoved`.
- [ ] **Step 2:** `cd cli && go test ./...` → FAIL (no compila).
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `go vet ./... && go test ./...` → PASS; `gofmt -l .` sin salida.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit `feat(cli): go module and config with env override`.

### Task 3 [Alumno]: Cliente HTTP de la API

**Lección previa:** `net/http` (cliente, `Request`, cabeceras), `defer resp.Body.Close()`, decodificar JSON a structs, errores propios con `errors.As`, `httptest.NewServer`.

**Files:**
- Create: `cli/internal/api/client.go`, `cli/internal/api/client_test.go`

**Interfaces:**
- Consumes: `config.Config` (Tarea 2).
- Produces:
  - `func New(baseURL, apiKey, userAgent string) *Client` (timeout 60 s, proxy del entorno).
  - `type Me struct { Email string; OrganizationID string; OrganizationName string; Role string }` y `func (c *Client) Me(ctx) (Me, error)`.
  - `type SbomSummary struct { ID, ProductID, VersionID, SpecVersion string; ComponentCount, ComponentsWithoutPurl int; Sha256 string }` y `func (c *Client) UploadSBOM(ctx, product, version string, createVersion bool, body io.Reader) (SbomSummary, bool /*replaced*/, error)` → `PUT /api/sbom?...` con `Content-Type: application/vnd.cyclonedx+json`.
  - `type Vulnerabilities struct { Status string; Error string; Items []Vulnerability }`, `type Vulnerability struct { ID string; Aliases []string; Severity string; Kev bool; ComponentName, ComponentVersion string }` y `func (c *Client) VersionVulnerabilities(ctx, productID, versionID string) (Vulnerabilities, error)`.
  - `type APIError struct { Status int; Title, Detail string; Errors map[string]string }` con `Error() string` = `"<status> <title>: <detail>"` más `field: message` ordenados; cualquier respuesta ≥ 400 lo devuelve (si el cuerpo no es problem details, usa el texto de estado). Fallo de red → error envuelto `"cannot reach CRA Desk at <url>: …"`.
  - Toda petición lleva `Authorization: Bearer <clave>` y `User-Agent`; la clave no aparece en ningún mensaje de error.
- [ ] **Step 1 (Claude):** `client_test.go` con `httptest`: `TestMeSendsBearerAndUserAgent`, `TestUploadSendsQueryAndBody` (201 → `replaced=false`; 200 → `true`; parámetros escapados con espacios), `TestProblemDetailsBecomeAPIError` (400 con `errors`, 404, 413), `TestNonJSONErrorStillBecomesAPIError`, `TestUnreachableServer`, `TestErrorsNeverContainTheKey`.
- [ ] **Step 2:** `go test ./...` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `go vet ./... && go test ./...` → PASS.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit `feat(cli): API client with problem-details errors`.

### Task 4 [Alumno]: Comandos `login`, `upload` y `version`

**Lección previa:** `flag.NewFlagSet` por subcomando, `io.Reader`/`io.Writer` para poder testear la salida, `context.WithTimeout`, `time.Ticker`, códigos de salida con `os.Exit` solo en `main`.

**Files:**
- Create: `cli/internal/app/app.go`, `cli/internal/app/app_test.go`, `cli/cmd/cradesk/main.go`

**Interfaces:**
- Consumes: `config` (Tarea 2) y `api` (Tarea 3).
- Produces:
  - `type Env struct { Args []string; Stdin io.Reader; Stdout, Stderr io.Writer; Getenv func(string) string; ConfigPath string; Version string; PollInterval time.Duration }` y `func Run(env Env) int` (devuelve el código de salida).
  - Constantes `ExitOK=0, ExitUsage=1, ExitAPI=2, ExitKEV=3, ExitScan=4`.
  - `cradesk login --api-url URL`: lee la clave de la primera línea de `Stdin`, llama a `Me`, guarda la configuración e imprime `Logged in to <org> (<url>)`.
  - `cradesk upload …` (flags de la tabla): valida configuración y archivo; sube; imprime `Uploaded SBOM for <product> <version>: <n> components (<m> without purl)` y si reemplazó `(replaced the previous SBOM)`; con `--wait`/`--fail-on-kev` espera e imprime una tabla corta (`KEV  ID  SEVERITY  COMPONENT`) y el total; devuelve `ExitKEV` si hay KEV y se pidió `--fail-on-kev`, `ExitScan` si `FAILED` o se pasa el tiempo.
  - `cradesk version` → `cradesk <versión>`; sin argumentos o `help` → uso en `Stderr` y `ExitUsage`.
  - `main.go`: `var version = "dev"` y `os.Exit(app.Run(app.Env{...}))`.
- [ ] **Step 1 (Claude):** `app_test.go` contra un `httptest.Server` que imita la API: `TestLoginStoresConfigAndNeverPrintsKey`, `TestLoginWithInvalidKeyExits2`, `TestUploadPrintsSummary`, `TestUploadMissingFileExits1`, `TestUploadWithoutConfigExits1`, `TestUploadAPIErrorExits2WithFieldErrors`, `TestWaitAndFailOnKevExits3`, `TestWaitWithoutKevExits0`, `TestScanFailedExits4`, `TestWaitTimeoutExits4`, `TestEnvVarsWorkWithoutLogin`, `TestUnknownCommandShowsUsage`.
- [ ] **Step 2:** `go test ./...` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `go vet ./... && go test ./...` → PASS; `go build ./cmd/cradesk` genera el binario.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit `feat(cli): login, upload and version commands with CI exit codes`.

### Task 5 [Claude]: GitHub Action, workflow de releases y binarios

**Files:**
- Create: `cli/action.yml`, `.github/workflows/cli-release.yml`, `scripts/build-cli.sh`

**Interfaces:**
- Produces:
  - `cli/action.yml` (composite) con las entradas de la tabla; la clave se pasa al paso como `env: CRADESK_API_KEY`, nunca como argumento.
  - `scripts/build-cli.sh`: compila con `-trimpath -ldflags "-s -w -X main.version=$VERSION"` para linux/darwin/windows × amd64/arm64 en `cli/dist/` (ignorado por git).
  - `.github/workflows/cli-release.yml`: en `push` de tags `cli-v*`, corre `go test`, `scripts/build-cli.sh` y crea el release con `gh release create` adjuntando los binarios y sus `sha256`.
- [ ] **Step 1:** Ejecutar `bash scripts/build-cli.sh` con `VERSION=0.1.0-local` → 6 binarios en `cli/dist/`; `cli/dist/cradesk-windows-amd64.exe version` imprime `cradesk 0.1.0-local`.
- [ ] **Step 2:** Validar la sintaxis de los dos YAML (parser de YAML de Python) y revisar a mano que el composite no imprime la clave (`set +x`, sin `echo` de entradas sensibles).
- [ ] **Step 3:** Commit `ci(cli): composite GitHub Action, cross-platform build and release workflow`.

### Task 6 [Claude]: Cierre de la Etapa 5

**Files:** `README.md` (sección CLI con instalación, `login`, `upload`, códigos de salida y ejemplo de la Action), `scripts/demo.sh` (la subida del paso 10 usa el binario `cradesk` si existe en `cli/dist/`, con `--wait`), `.gitignore` (`cli/dist/`)

- [ ] **Step 1:** `demo.sh`: compilar la CLI si falta, y en el paso 10 usar `CRADESK_API_URL`/`CRADESK_API_KEY` con `cradesk upload --product "Smart Lock" --version 1.0.0 --file scripts/sample-sbom.cdx.json --wait`, mostrando el código de salida; agregar un paso con `--fail-on-kev` que debe salir con 3.
- [ ] **Step 2:** Arrancar la API con red y correr `demo.sh`. Resultado esperado: cada paso con su código.
- [ ] **Step 3:** Revisión de toda la rama con un revisor independiente y corrección de lo crítico e importante con TDD.
- [ ] **Step 4:** Commit `docs: stage 5 README and demo with the Go CLI`.
- [ ] **Step 5:** Preguntar al usuario por el merge y el push, y si quiere publicar el primer release (`git tag cli-v0.1.0`), que es una acción pública.

---

**Fuera de alcance de esta etapa:**
- Generar el SBOM (Syft lo hace; la Action puede encadenarse con `anchore/sbom-action`): spec §2, no-meta.
- Firmar binarios o atestaciones SLSA: Etapa 8 si hay tiempo.
- Homebrew/Scoop: después de diciembre.
- CI que corre los tests de Java y Go en cada push: Etapa 8.
