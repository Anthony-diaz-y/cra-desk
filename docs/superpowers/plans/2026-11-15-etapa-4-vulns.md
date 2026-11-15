# CRA Desk — Etapa 4: `vulns` — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Modo de trabajo:** el usuario elige cada día entre **tutor** y **avance directo**. En modo tutor, las tareas **[Alumno]** siguen este ciclo: lección corta con un ejemplo de otro dominio, Claude escribe el test, el alumno escribe el código, Claude revisa con pistas progresivas y el alumno anota 3 líneas en `docs/aprendizaje.md`. Las tareas **[Claude]** las hace Claude y luego las explica. En avance directo, Claude implementa todo con TDD.

**Goal:** Cruzar los componentes de cada SBOM con las vulnerabilidades conocidas de OSV, marcar las que están en el catálogo KEV de CISA (explotadas activamente) y crear por cada una de esas un **hallazgo para revisar**. Se escanea al subir un SBOM y cada día.

**Architecture:** Nuevo módulo `io.cradesk.api.vulns`, que depende de `sbom`, `catalog` e `identity`; ninguno de ellos depende de `vulns`. `sbom` publica un evento de Spring (`SbomUploadedEvent`) y `vulns` lo escucha después del commit para encolar el escaneo. Los clientes HTTP de OSV y KEV son interfaces (`OsvClient`, `KevFeedClient`) con implementación `RestClient`; los tests de integración usan dobles en memoria y los clientes HTTP se prueban aparte con `MockRestServiceServer`. Un planificador `@Scheduled` procesa la cola de escaneos y hace la pasada diaria.

**Tech Stack:** el de las etapas anteriores. `RestClient` de Spring (ya incluido), `@Scheduled`, `ApplicationEventPublisher` y `@TransactionalEventListener`. Sin librerías nuevas.

**Spec:** `docs/superpowers/specs/2026-11-13-cra-desk-design.md` (§2: OSV, KEV y "una vulnerabilidad del catálogo KEV crea un hallazgo para revisar (no abre el evento por sí sola)"; §5 módulo `vulns`; §7 "cada cuánto se re-escanea: diario y al subir un SBOM").

## Global Constraints

- Código, identificadores, mensajes de la API y README en inglés. Explicaciones y `docs/aprendizaje.md` en español.
- **Comentarios:** solo un comentario de una línea en español encima de cada función o método (también tests, constructores y métodos de repositorio). Ningún otro comentario en código ni configuración.
- Errores como `ProblemDetail` a través de `ApiExceptionHandler` y las excepciones base de `common`.
- Toda lectura por empresa: a una versión se llega por `ProductService.findVersion`; los hallazgos guardan `organization_id` y se filtran por `CurrentTenant`. Un id de otra empresa da 404.
- Un hallazgo **no** abre un evento reportable ni arranca relojes: eso lo hace una persona en la Etapa 6.
- Las llamadas externas (OSV, KEV) nunca ocurren dentro de una petición HTTP de un usuario ni dentro de una transacción de base de datos; tienen timeouts explícitos.
- Migraciones nuevas: no se edita ninguna aplicada; restricciones con nombre; `varchar`/`text`, no `char`.
- Maven wrapper 3.9.16. Tests con `./mvnw clean verify`. Push solo con confirmación del usuario.

## Decisiones de diseño de esta etapa (a criterio de quien implementa, spec §7)

| Tema | Decisión | Por qué |
|---|---|---|
| Fuente de vulnerabilidades | OSV: `POST https://api.osv.dev/v1/querybatch` con hasta 1000 purls por llamada (solo devuelve ids y `modified`), y `GET /v1/vulns/{id}` para el detalle de cada id nuevo o modificado. Base URL configurable (`cradesk.osv.base-url`). | Es gratis, sin clave, entiende purl directamente y cubre Maven, npm, PyPI, Go, Alpine, Debian, etc. |
| Qué componentes se escanean | Los que tienen purl **con versión** (`@`). El resto se cuenta como `componentsNotScanned`. | OSV necesita la versión para saber si está afectada. |
| Paginación de OSV | Si un resultado trae `next_page_token`, se repite esa consulta con el token hasta agotarlo. | Paquetes con muchas vulnerabilidades devuelven el resultado en páginas. |
| Caché de vulnerabilidades | Tabla `vulnerabilities` (id OSV, resumen, alias CVE, severidad, fechas). Solo se pide el detalle si el id es nuevo o su `modified` cambió. | Evita miles de llamadas repetidas en el escaneo diario. |
| Severidad | Se guarda tal cual: `database_specific.severity` si existe (p. ej. `HIGH`) y, si no, el primer vector CVSS de `severity[]`. No se calcula un puntaje. | Calcular CVSS desde el vector es trabajo aparte; el texto basta para ordenar y mostrar. |
| KEV | Se descarga el JSON oficial (`https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json`, configurable) al arrancar si la tabla está vacía y luego cada día. Se reemplaza la tabla entera en una transacción; si la descarga o el JSON fallan, se conserva el catálogo anterior. | Es la lista oficial de vulnerabilidades explotadas que el CRA obliga a reportar. |
| Cruce con KEV | Una vulnerabilidad está en KEV si su id o cualquiera de sus alias es un `CVE-…` presente en `kev_entries` (comparación en mayúsculas). | OSV suele identificar por GHSA y pone el CVE en `aliases`. |
| Hallazgos | Uno por (versión, vulnerabilidad KEV), con estado `PENDING_REVIEW`. Re-escanear no duplica; si la vulnerabilidad ya no aparece (SBOM corregido), el hallazgo queda con `present = false`, no se borra. | Es un registro de cumplimiento. La revisión (confirmar o descartar con motivo) es de la Etapa 6. |
| Cuándo se escanea | (1) después del commit de una subida de SBOM, vía `SbomUploadedEvent`; (2) cada día a las 03:00 UTC, todas las versiones con SBOM; (3) a pedido con `POST …/scan`. Todo pasa por una cola en la tabla `version_scans` (`PENDING` → `RUNNING` → `DONE`/`FAILED`) que un `@Scheduled` revisa cada 30 s. | La subida responde al instante y OSV puede tardar o fallar sin afectar al usuario. Railway corre una sola instancia; con varias haría falta ShedLock (Etapa 8). |
| Fallos | Error de red, 5xx, timeout (conexión 5 s, lectura 30 s) o JSON inválido → el escaneo queda `FAILED` con el motivo y **sin cambios parciales** en coincidencias ni hallazgos; se reintenta en la próxima pasada diaria o a pedido. | Una caída de OSV no debe dejar datos a medias ni tumbar la API. |
| Tests | Los ITs usan dobles en memoria de `OsvClient` y `KevFeedClient` (`@TestConfiguration`, `@Primary`) y el planificador desactivado (`cradesk.scan.scheduler-enabled=false`). Los clientes HTTP se prueban con `MockRestServiceServer` y respuestas JSON guardadas. | Tests rápidos, deterministas y sin red. La forma real de las respuestas se comprueba en vivo en la Tarea 7. |

## Review Focus

1. **Servicios externos caídos o raros:** OSV con 500, timeout o JSON inválido, o KEV inaccesible, deben dejar el escaneo en `FAILED` con motivo, sin coincidencias ni hallazgos a medias, con la API funcionando y el catálogo KEV anterior intacto (Tareas 3, 4 y 5).
2. **SBOM reales:** componentes sin purl o sin versión, purls que OSV no conoce, 20 000 componentes (lotes de 1000) y resultados paginados con `next_page_token` (Tareas 2 y 4).
3. **Re-escaneo idempotente:** re-escanear o reemplazar el SBOM no duplica hallazgos; uno que ya no aparece queda `present = false` (Tarea 4).
4. **Entre empresas:** las vulnerabilidades de una versión y la lista de hallazgos solo muestran lo propio; ids de otra empresa dan 404; la demo puede leer pero no pedir un escaneo (Tarea 6).
5. **Cruce con KEV por alias:** una vulnerabilidad GHSA con alias `CVE-2021-44228` se marca KEV aunque su id no sea CVE; mayúsculas y minúsculas no importan (Tareas 3 y 4).

---

### Task 1 [Alumno]: Tablas y entidades de vulnerabilidades (`V9`)

**Lección previa:** tablas puente (coincidencias), arreglos de PostgreSQL (`text[]`) con Hibernate, `ON DELETE CASCADE` encadenado.

**Files:**
- Create: `api/src/main/resources/db/migration/V9__vulns.sql`
- Create en `api/src/main/java/io/cradesk/api/vulns/`: `ScanStatus.java`, `VersionScan.java`, `Vulnerability.java`, `KevEntry.java`, `VulnerabilityMatch.java`, `FindingStatus.java`, `Finding.java` y un repositorio por entidad
- Test: `api/src/test/java/io/cradesk/api/vulns/VulnsRepositoryIT.java`

**Interfaces:**
- Produces:
  - `version_scans`: `product_version_id uuid pk` (FK `version_scans_version_fk`), `status varchar(16) not null` (`CHECK IN ('PENDING','RUNNING','DONE','FAILED')`), `requested_at timestamptz not null`, `started_at`, `finished_at`, `error varchar(500)`, `components_scanned int`, `components_not_scanned int`.
  - `vulnerabilities`: `id varchar(100) pk`, `summary varchar(1000)`, `severity varchar(200)`, `aliases text[] not null`, `published timestamptz`, `modified timestamptz not null`, `fetched_at timestamptz not null`.
  - `kev_entries`: `cve_id varchar(30) pk`, `vendor_project varchar(200)`, `product varchar(200)`, `vulnerability_name varchar(500)`, `date_added date not null`, `due_date date`, `known_ransomware varchar(20)`, `short_description varchar(2000)`, `fetched_at timestamptz not null`.
  - `vulnerability_matches`: `id uuid pk`, `product_version_id uuid not null` (FK), `component_id uuid not null` (FK a `sbom_components` `ON DELETE CASCADE`), `vulnerability_id varchar(100) not null` (FK), `kev boolean not null`, único `vulnerability_matches_unique (component_id, vulnerability_id)`, índice por `product_version_id`.
  - `findings`: `id uuid pk`, `organization_id uuid not null` (FK), `product_version_id uuid not null` (FK), `vulnerability_id varchar(100) not null` (FK), `cve_id varchar(30) not null`, `status varchar(20) not null` (`CHECK IN ('PENDING_REVIEW')`, la Etapa 6 amplía), `present boolean not null`, `created_at timestamptz not null`, `last_seen_at timestamptz not null`, único `findings_version_vuln_unique (product_version_id, vulnerability_id)`, índice `(organization_id, status)`.
  - Constante `Finding.VERSION_VULN_UNIQUE = "findings_version_vuln_unique"`.
  - Entidades con getters y los métodos de dominio: `VersionScan.request(Instant)`, `start(Instant)`, `finish(Instant, int scanned, int notScanned)`, `fail(Instant, String error)` (recorta a 500); `Finding.seenAt(Instant)` (marca `present = true`), `markAbsent()`.
  - `Vulnerability.aliases` como `List<String>` mapeada a `text[]` (comprobar con `ddl-auto=validate`; si hace falta, `@JdbcTypeCode(SqlTypes.ARRAY)`).
  - Repositorios: `VersionScanRepository.findFirstByStatusOrderByRequestedAtAsc(ScanStatus)`, `KevEntryRepository.findAllByCveIdIn(Collection<String>)`, `VulnerabilityMatchRepository.deleteByProductVersionId(UUID)` y `findByProductVersionId(UUID)`, `FindingRepository.findByProductVersionId(UUID)` y `findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID, FindingStatus)`.

- [ ] **Step 1 (Claude):** Escribir `VulnsRepositoryIT` (`@Transactional`) con: `savesVulnerabilityWithAliases` (la lista de alias vuelve igual), `matchesAreDeletedWithTheirComponent` (borrar el SBOM borra componentes y coincidencias), `oneFindingPerVersionAndVulnerability` (segundo hallazgo igual → `Constraints.isViolated(e, Finding.VERSION_VULN_UNIQUE)`), `nextPendingScanIsTheOldest`.
- [ ] **Step 2:** `./mvnw clean verify -Dit.test=VulnsRepositoryIT` → FAIL por compilación.
- [ ] **Step 3 (Alumno):** Migración, enums, entidades y repositorios.
- [ ] **Step 4:** `./mvnw clean verify` → PASS en todo.
- [ ] **Step 5:** Commit `feat(vulns): vulnerability, kev, match, finding and scan tables (V9)`.

### Task 2 [Claude]: Cliente de OSV

**Files:**
- Create en `vulns/`: `OsvClient.java` (interfaz), `OsvHttpClient.java`, `OsvVulnerability.java`, `OsvProperties.java`, `ExternalServiceException.java`
- Create fixtures en `api/src/test/resources/osv/`: `querybatch-response.json` (3 consultas: una con 2 ids, una vacía, una con `next_page_token`), `querybatch-page2.json`, `vuln-GHSA-jfh8-c2jp-5v3q.json` (log4j, alias `CVE-2021-44228`, `database_specific.severity` `CRITICAL`), `vuln-with-cvss-only.json`
- Test: `api/src/test/java/io/cradesk/api/vulns/OsvHttpClientTest.java`

**Interfaces:**
- Produces:
  - `interface OsvClient` con `Map<String, List<OsvHit>> queryByPurl(List<String> purls)` (purl → ids con su `modified`, ya paginado) y `OsvVulnerability fetch(String id)`.
  - `record OsvHit(String id, Instant modified)`; `record OsvVulnerability(String id, String summary, String severity, List<String> aliases, Instant published, Instant modified)`.
  - `OsvHttpClient` sobre `RestClient` con `cradesk.osv.base-url` (por defecto `https://api.osv.dev`), timeouts de conexión 5 s y lectura 30 s, lotes de `BATCH_SIZE = 1000`. Cualquier fallo de red, estado ≥ 400 o JSON inesperado → `ExternalServiceException("OSV: <motivo corto>")`.
  - `ExternalServiceException extends RuntimeException` (no llega nunca a un usuario: la atrapa el escáner).

- [ ] **Step 1:** Escribir `OsvHttpClientTest` con `MockRestServiceServer` (sin Spring): `queriesInBatchesOf1000` (2 001 purls → 3 POST), `followsNextPageToken` (la consulta con token se repite con `page_token` y se juntan los ids), `parsesVulnerabilityDetail` (resumen, `CRITICAL`, alias `CVE-2021-44228`), `fallsBackToCvssVector`, `serverErrorBecomesExternalServiceException`, `invalidJsonBecomesExternalServiceException`.
- [ ] **Step 2:** `./mvnw clean test -Dtest=OsvHttpClientTest` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(vulns): OSV client with batching and pagination`.

### Task 3 [Claude]: Catálogo KEV de CISA

**Files:**
- Create en `vulns/`: `KevFeedClient.java` (interfaz), `KevHttpClient.java`, `KevCatalogService.java`, `KevProperties.java`
- Create fixture: `api/src/test/resources/kev/known_exploited_vulnerabilities.json` (3 entradas reales recortadas, incluida `CVE-2021-44228`)
- Test: `api/src/test/java/io/cradesk/api/vulns/KevHttpClientTest.java`, `api/src/test/java/io/cradesk/api/vulns/KevCatalogIT.java`
- Create (test): `api/src/test/java/io/cradesk/api/support/FakeExternalFeeds.java` (dobles `FakeOsvClient` y `FakeKevFeedClient` con `@Primary`, importados por `@IntegrationTest`)

**Interfaces:**
- Produces:
  - `interface KevFeedClient` con `List<KevRecord> download()`; `record KevRecord(String cveId, String vendorProject, String product, String vulnerabilityName, LocalDate dateAdded, LocalDate dueDate, String knownRansomware, String shortDescription)`.
  - `KevHttpClient`: URL `cradesk.kev.url`, mismos timeouts; fallo → `ExternalServiceException("KEV: …")`.
  - `KevCatalogService.refresh()` → `int` entradas cargadas: descarga **fuera** de la transacción y luego reemplaza `kev_entries` en una transacción. Si la descarga falla o trae 0 entradas, lanza `ExternalServiceException` y la tabla queda como estaba. `boolean isEmpty()`. `Set<String> kevCves(Collection<String> ids)` (comparación en mayúsculas).
  - `FakeOsvClient` / `FakeKevFeedClient`: respuestas programables por test (`willReturn…`, `willFail()`), `reset()` en `@AfterEach`.

- [ ] **Step 1:** Tests: `KevHttpClientTest.parsesOfficialFeed` (3 registros, fechas como `LocalDate`); `KevCatalogIT.refreshReplacesCatalog`, `failedDownloadKeepsPreviousCatalog`, `emptyFeedKeepsPreviousCatalog`, `kevLookupIgnoresCase`.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(vulns): CISA KEV catalog with atomic refresh`.

### Task 4 [Alumno]: Escáner de una versión

**Lección previa:** separar "llamar afuera" de "escribir en la BD"; transacciones cortas; idempotencia (insertar si no existe, actualizar si existe).

**Files:**
- Create en `vulns/`: `VersionScanner.java`, `ScanQueue.java`
- Create en `sbom/`: `SbomUploadedEvent.java`; Modify: `SbomService.java` (publica el evento al guardar)
- Test: `api/src/test/java/io/cradesk/api/vulns/VersionScannerIT.java`

**Interfaces:**
- Consumes: `OsvClient`, `KevCatalogService` (Tareas 2 y 3), repositorios (Tarea 1), `SbomRepository`/`SbomComponentRepository` (Etapa 3).
- Produces:
  - `record SbomUploadedEvent(UUID productVersionId)` publicado por `SbomService.store` con `ApplicationEventPublisher`.
  - `ScanQueue.request(UUID versionId)`: crea o pone `PENDING` su `VersionScan` (si ya está `PENDING` o `RUNNING`, no hace nada). Escucha `SbomUploadedEvent` con `@TransactionalEventListener(phase = AFTER_COMMIT)` y encola en una transacción nueva.
  - `VersionScanner.scan(UUID versionId)`:
    1. Transacción corta: marca `RUNNING` y lee los componentes con purl y versión.
    2. Fuera de transacción: `osv.queryByPurl(...)` y `osv.fetch(id)` para los ids nuevos o con `modified` distinto.
    3. Transacción: guarda o actualiza `vulnerabilities`; borra y vuelve a crear las `vulnerability_matches` de la versión; calcula KEV; crea los hallazgos KEV que falten (`PENDING_REVIEW`, `present = true`), marca `seenAt` los que siguen y `markAbsent` los que ya no aparecen; `finish(...)`.
    4. Si 2 o 3 fallan: transacción nueva que solo hace `fail(...)`; nada más cambia.
  - `VersionScanner.scanNextPending()` → `boolean` (si había algo para escanear).

- [ ] **Step 1 (Claude):** Escribir `VersionScannerIT` (sin transacción, con `TRUNCATE` y los dobles de la Tarea 3), sobre un SBOM con log4j-core 2.14.1, lodash 4.17.15, un componente sin purl y uno con purl sin versión:
  - `uploadQueuesAScan`: subir un SBOM deja la versión `PENDING`.
  - `scanCreatesMatchesAndKevFinding`: OSV devuelve GHSA de log4j (alias `CVE-2021-44228`, que está en KEV) y uno de lodash (no KEV) → 2 coincidencias, 1 hallazgo `PENDING_REVIEW` con `cve_id` `CVE-2021-44228`, escaneo `DONE` con `componentsScanned` 2 y `componentsNotScanned` 2.
  - `rescanDoesNotDuplicateFindings` y `findingNoLongerPresentIsKeptAsAbsent` (re-subir el SBOM sin log4j y re-escanear).
  - `unchangedVulnerabilityIsNotFetchedAgain`: segundo escaneo con el mismo `modified` → `fetch` no se llama.
  - `osvFailureMarksFailedWithoutPartialData`: `willFail()` → `FAILED` con motivo; ninguna coincidencia ni hallazgo nuevo.
  - `requestWhilePendingIsANoOp`.
- [ ] **Step 2:** `./mvnw clean verify -Dit.test=VersionScannerIT` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(vulns): scan sbom components against OSV and KEV into findings`.

### Task 5 [Claude]: Planificador

**Files:**
- Create en `vulns/`: `ScanScheduler.java`, `ScanProperties.java`; Modify: `ApiApplication.java` (`@EnableScheduling`), `application.yml`, `src/test/resources/application.yml` o la propiedad en `@IntegrationTest` (`cradesk.scan.scheduler-enabled=false`)
- Test: `api/src/test/java/io/cradesk/api/vulns/ScanSchedulerIT.java`

**Interfaces:**
- Produces:
  - `ScanProperties(boolean schedulerEnabled, Duration pollInterval, String dailyCron)`; por defecto `true`, `PT30S`, `0 0 3 * * *` (zona UTC).
  - `ScanScheduler.processQueue()` (`@Scheduled(fixedDelayString = "${cradesk.scan.poll-interval:PT30S}")`): escanea pendientes hasta vaciar la cola o hasta 20 por pasada.
  - `ScanScheduler.daily()` (`@Scheduled(cron = …, zone = "UTC")`): `kev.refresh()` (si falla, se registra en el log y se sigue) y encola todas las versiones con SBOM.
  - Al arrancar (`ApplicationReadyEvent`): si `kev.isEmpty()`, intenta `refresh()` sin impedir el arranque si falla.
  - Con `scheduler-enabled=false` los métodos programados no hacen nada (los tests llaman a los servicios directamente).

- [ ] **Step 1:** Tests: `dailyRefreshesKevAndQueuesEveryVersionWithSbom`, `kevFailureDoesNotStopTheDailyQueue`, `processQueueDrainsPendingScans` (llamando a los métodos con el planificador habilitado por un `@BeforeEach` que crea su propio `ScanScheduler` con propiedades `true`).
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(vulns): scheduled scan queue and daily rescan`.

### Task 6 [Alumno]: Endpoints de vulnerabilidades y hallazgos

**Files:**
- Create en `vulns/`: `VulnsController.java`, `VulnsService.java`, `VersionVulnerabilities.java`, `VulnerabilityResponse.java`, `FindingResponse.java`
- Test: `api/src/test/java/io/cradesk/api/vulns/VulnsApiIT.java`

**Interfaces:**
- Consumes: `ProductService.findVersion`, `CurrentTenant`, `ScanQueue.request`, repositorios.
- Produces:
  - `GET /api/products/{id}/versions/{versionId}/vulnerabilities` → 200 `VersionVulnerabilities(ScanStatus status, Instant finishedAt, String error, int componentsScanned, int componentsNotScanned, List<VulnerabilityResponse> items)`; `VulnerabilityResponse(String id, List<String> aliases, String summary, String severity, boolean kev, LocalDate kevDateAdded, String componentName, String componentVersion, String purl)`, ordenado con KEV primero y luego por id. Sin escaneo todavía → `status` `null` y lista vacía. Sin SBOM → 404 `"No SBOM uploaded for this version"`.
  - `POST /api/products/{id}/versions/{versionId}/scan` → 202 (encola). Sin SBOM → 404. `DEMO` → 403 (reglas de la Etapa 2).
  - `GET /api/findings` → 200 `List<FindingResponse(UUID id, UUID productId, String productName, UUID versionId, String version, String vulnerabilityId, String cveId, String summary, String severity, LocalDate kevDateAdded, boolean present, FindingStatus status, Instant createdAt)>` de la empresa actual, `PENDING_REVIEW`, más recientes primero.

- [ ] **Step 1 (Claude):** `VulnsApiIT`: `versionVulnerabilitiesAfterScan`, `versionWithoutScanShowsEmptyList`, `scanRequestReturns202AndQueues`, `demoCannotRequestScan`, `findingsListShowsOnlyOwnOrganization`, `otherTenantsVersionReturns404`.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(vulns): version vulnerabilities, scan request and findings endpoints`.

### Task 7 [Claude]: Cierre de la Etapa 4

**Files:** `README.md` (estado, tabla de la API, variables `CRADESK_OSV_BASE_URL`, `CRADESK_KEV_URL`, `CRADESK_SCAN_SCHEDULER_ENABLED`), `scripts/demo.sh`, `.env.example`

- [ ] **Step 1:** `demo.sh`: después de subir el SBOM, pedir el escaneo, esperar (hasta 90 s, consultando cada 3 s) a que quede `DONE` y mostrar las vulnerabilidades y la lista de hallazgos. Usar un SBOM con log4j-core 2.14.1 para que aparezca el hallazgo KEV `CVE-2021-44228`.
- [ ] **Step 2:** Arrancar la API **con red** y correr `demo.sh` contra OSV y KEV reales. Esto confirma la forma real de las respuestas que los tests simulan; si algo difiere, corregir el cliente y su fixture con TDD.
- [ ] **Step 3:** Revisión de toda la rama con un revisor independiente y corrección de lo crítico e importante con TDD.
- [ ] **Step 4:** Commit `docs: stage 4 README and demo`.
- [ ] **Step 5:** Preguntar al usuario si quiere el merge y el push a `origin/main`.

---

**Fuera de alcance de esta etapa (anotado):**
- Revisar hallazgos (confirmar o descartar con motivo), eventos reportables y relojes: Etapa 6.
- Puntaje CVSS calculado, EPSS y filtros avanzados: después de diciembre.
- ShedLock o bloqueo distribuido del planificador: Etapa 8, solo si hay más de una instancia.
- Notificaciones por email/Slack de hallazgos nuevos: no-meta de diciembre (spec §2).
