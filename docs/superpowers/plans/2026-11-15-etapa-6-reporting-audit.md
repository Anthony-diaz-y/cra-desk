# CRA Desk — Etapa 6: `reporting` y `audit` — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Modo de trabajo:** el usuario elige cada día entre **tutor** y **avance directo**. En modo tutor, las tareas **[Alumno]** siguen este ciclo: lección corta con un ejemplo de otro dominio, Claude escribe el test, el alumno escribe el código, Claude revisa con pistas progresivas y el alumno anota 3 líneas en `docs/aprendizaje.md`. Las tareas **[Claude]** las hace Claude y luego las explica. En avance directo, Claude implementa todo con TDD.

**Goal:** Una persona revisa cada hallazgo KEV y lo confirma o lo descarta con motivo. Confirmar abre un **evento reportable** con tres relojes (24 h, 72 h, 14 días) y tres reportes. Cada reporte tiene un borrador (IA o plantilla) que la persona edita, aprueba, exporta y marca "enviado a ENISA", lo que detiene su reloj. Toda acción queda en una **auditoría inmutable con cadena de hashes** por empresa, verificable.

**Architecture:** Dos módulos nuevos: `io.cradesk.api.reporting` (eventos, reportes, relojes, borradores) e `io.cradesk.api.audit` (registro encadenado). `reporting` depende de `vulns`, `catalog`, `identity` y `audit`; `audit` solo de `common`/`identity`. La IA va detrás de una interfaz `DraftGenerator` con tres implementaciones elegidas por configuración: `template` (sin IA), `claude` (Claude Haiku 5.5 con el SDK oficial de Java) y `openai-compatible` (HTTP, para DeepSeek u otro proveedor con ese formato). Los relojes se calculan con una función pura sobre el `Clock` de Spring.

**Tech Stack:** el de las etapas anteriores, más `com.anthropic:anthropic-java` (SDK oficial). Sin otras librerías.

**Spec:** `docs/superpowers/specs/2026-11-13-cra-desk-design.md` (§2: hallazgo para revisar, confirmación que abre el evento y arranca los relojes, descarte con motivo auditado, "enviado a ENISA" con hora y referencia opcional, estados de cada plazo, la IA redacta y una persona aprueba, exportación PDF/JSON, auditoría con cadena de hashes, demo sin llamadas a IA y tope diario para invitados; §5 módulos `reporting` y `audit`; §6 etapa 6; §7 formato del borrador según el art. 14, modelo Haiku, una cadena de auditoría por empresa).

## Global Constraints

- Código, mensajes de la API y README en inglés. Explicaciones y `docs/aprendizaje.md` en español.
- **Comentarios:** solo un comentario de una línea en español encima de cada función o método (también tests). Ningún otro comentario en código, SQL, YAML ni scripts.
- **Commits sin la línea `Co-Authored-By` de Claude** y **sin push**: el usuario hace el push al final.
- Errores como `ProblemDetail` mediante las excepciones base de `common`; todo filtrado por empresa (`CurrentTenant`); id de otra empresa → 404; `DEMO` solo lee.
- Las claves de IA solo en variables de entorno (`.env`, nunca en git ni en el chat). Los tests **nunca** llaman a una IA real.
- Un hallazgo pendiente no abre relojes; solo una confirmación humana lo hace. Los plazos vencidos no bloquean nada: solo se marcan.
- Maven wrapper 3.9.16; tests con `./mvnw clean verify`.

## Decisiones de diseño de esta etapa

| Tema | Decisión | Por qué |
|---|---|---|
| Revisión del hallazgo | `POST /api/findings/{id}/confirm` y `POST /api/findings/{id}/dismiss {reason}` (motivo obligatorio, 10–1000 caracteres). Solo desde `PENDING_REVIEW`; repetir o cambiar de opinión → 409. Estados: `PENDING_REVIEW`, `CONFIRMED`, `DISMISSED`. | Spec §2. Un descarte sin motivo no se audita bien. |
| Evento reportable | Uno por hallazgo confirmado: guarda empresa, versión, CVE, quién confirmó y cuándo. Los tres plazos se fijan **una vez** al confirmar: `confirmedAt + 24 h`, `+ 72 h`, `+ 14 días`. | Spec §2: los relojes arrancan con la confirmación. |
| Estados de cada plazo | Calculados, no guardados: `PENDING` (falta más del 25 % del plazo), `DUE_SOON` (falta 25 % o menos), `OVERDUE` (vencido sin enviar), `SENT_ON_TIME`, `SENT_LATE`. | Spec §2 literal; calcularlo evita datos desactualizados. |
| Reportes | Tres por evento: `EARLY_WARNING`, `NOTIFICATION`, `FINAL_REPORT`. Estado del borrador: `EMPTY` → `DRAFTED` → `APPROVED`. Editar un aprobado lo vuelve a `DRAFTED`. Marcar enviado exige `APPROVED`; con `sentAt` (por defecto ahora, nunca futuro) y `enisaReference` opcional (≤ 100). Enviado es definitivo (409 si se repite). | Spec §2: aprobar no detiene el reloj; solo "enviado". |
| Contenido del borrador | JSON con campos fijos por tipo, siguiendo el art. 14 del CRA. **Aviso temprano:** `summary`, `activelyExploited`, `suspectedMaliciousAct`, `memberStatesAffected`, `immediateMeasures`. **Notificación:** los anteriores más `vulnerabilityDescription`, `generalNature`, `correctiveMeasuresAvailable`, `mitigationsForUsers`, `sensitivity`. **Informe final:** `vulnerabilityDescription`, `severityAndImpact`, `maliciousActorInformation`, `securityUpdateDetails`, `lessonsLearned`. | Spec §7. Campos fijos sirven igual para plantilla, IA y exportación. |
| Proveedor de IA | Interfaz `DraftGenerator` con `template`, `claude` y `openai-compatible`, elegida con `CRADESK_AI_PROVIDER` (por defecto `template`). `claude`: modelo `claude-haiku-5-5`, salida estructurada al record del tipo, `ANTHROPIC_API_KEY`. `openai-compatible`: `CRADESK_AI_BASE_URL` (p. ej. `https://api.deepseek.com`), `CRADESK_AI_MODEL` (p. ej. `deepseek-chat`), `CRADESK_AI_API_KEY`, `POST {base}/chat/completions` con `response_format: {type: json_object}`. Timeouts de 60 s. | Decisión del usuario (2026-11-15): proveedor intercambiable; usará DeepSeek. |
| Si la IA falla | Error de red, timeout, respuesta vacía o JSON que no encaja → se usa la plantilla y la respuesta lo dice (`generatedBy: TEMPLATE`, `aiError` corto); nunca 500. La plantilla no cuenta para el tope. | El reloj corre: el usuario siempre recibe un borrador. |
| Prompt | Sistema fijo en inglés: rol de "asistente de redacción de reportes CRA, no asesor legal", campos a completar, "usa solo los datos dados; si falta un dato escribe `TO BE COMPLETED`". Los datos del cliente (nombres de componentes, etc.) van en un bloque aparte marcado como datos, nunca como instrucciones. | Reduce invenciones y el riesgo de inyección desde el SBOM; la persona aprueba igual. |
| Tope diario | 20 borradores con IA por empresa y día UTC (`CRADESK_AI_DAILY_LIMIT`), contados en `ai_usage (organization_id, day)` con `INSERT … ON CONFLICT DO UPDATE` atómico; al pasarlo → 429 `"Daily AI draft limit reached (20)"`. | Decisión del usuario (2026-11-15). |
| Demo | La demo tiene un evento ya confirmado con sus tres borradores pregenerados y uno enviado; `DEMO` no puede pedir borradores (403) y nunca llama a la IA. | Spec §2. |
| Exportación | `GET …/reports/{type}/export?format=json|markdown` (descarga con nombre de archivo saneado). PDF fuera de alcance. | Spec §6: si no alcanza el tiempo, se recorta el PDF primero. |
| Auditoría | Tabla `audit_entries` append-only con `seq` por empresa, `prev_hash` y `hash = SHA-256(prev_hash ‖ seq ‖ occurred_at ‖ actor ‖ action ‖ target ‖ details canónicos)`. La cabeza de cada cadena (`audit_chain_heads`) se bloquea con `SELECT … FOR UPDATE` para encadenar sin carreras. Un trigger de PostgreSQL rechaza `UPDATE` y `DELETE`. Se registra en la **misma transacción** que la acción. | Spec §2 y §5; §7: una cadena por empresa. |
| Qué se audita | Confirmar y descartar hallazgos, generar (con proveedor), editar, aprobar, exportar y marcar enviado un reporte; subir SBOM; crear y revocar claves de API. | Lo que un auditor preguntaría. |
| Endpoints de auditoría | `GET /api/audit?page&size` (más reciente primero) y `GET /api/audit/verify` → `{valid, entries, firstInvalidSeq}`. Lectura para `MEMBER` y `DEMO`. | Spec §5: endpoint de verificación. |

## Review Focus

1. **Relojes en los bordes:** exactamente en el 75 % del plazo, en el segundo del vencimiento, enviado un segundo antes o después del plazo, `sentAt` futuro (400) y reportes enviados fuera de orden (Tareas 1 y 4).
2. **IA que falla o devuelve basura:** timeout, 401 por clave mala, JSON inválido o con campos faltantes, texto con instrucciones metidas en un nombre de componente → borrador de plantilla con `aiError`, nunca 500 ni datos inventados fuera de los campos (Tareas 3 y 4).
3. **Tope diario con concurrencia:** 20 peticiones simultáneas cuando faltan 2 → solo 2 pasan; el día cambia en UTC (Tarea 4).
4. **Auditoría:** dos acciones simultáneas de la misma empresa no rompen la cadena; una fila modificada a mano (saltando el trigger) se detecta en `verify` con su `seq`; cada empresa tiene su propia cadena (Tarea 5).
5. **Entre empresas y demo:** hallazgos, eventos, reportes, exportaciones y auditoría de otra empresa dan 404 o no aparecen; la demo lee todo y no puede escribir (Tareas 2, 4, 5 y 6).

---

### Task 1 [Alumno]: Relojes del CRA como función pura

**Lección previa:** `Instant` y `Duration`, `enum` con lógica, tests unitarios sin Spring y por qué conviene inyectar el reloj.

**Files:** Create en `api/src/main/java/io/cradesk/api/reporting/`: `ReportType.java`, `DeadlineStatus.java`, `Deadlines.java`. Test: `api/src/test/java/io/cradesk/api/reporting/DeadlinesTest.java`.

**Interfaces:**
- `enum ReportType { EARLY_WARNING(Duration.ofHours(24)), NOTIFICATION(Duration.ofHours(72)), FINAL_REPORT(Duration.ofDays(14)) }` con `Duration window()` e `Instant dueAt(Instant confirmedAt)`.
- `enum DeadlineStatus { PENDING, DUE_SOON, OVERDUE, SENT_ON_TIME, SENT_LATE }`.
- `final class Deadlines` con `static DeadlineStatus status(Instant start, Instant due, Instant sentAt, Instant now)`: enviado → `SENT_ON_TIME` si `sentAt <= due`, si no `SENT_LATE`; sin enviar → `OVERDUE` si `now > due`, `DUE_SOON` si lo que queda es ≤ 25 % de `due − start`, si no `PENDING`.

- [ ] **Step 1 (Claude):** `DeadlinesTest`: `windowsMatchTheCra` (24 h, 72 h, 14 d), `pendingBeforeThreeQuarters`, `dueSoonAtExactlyTwentyFivePercentLeft`, `overdueOneSecondAfterDue`, `notOverdueAtExactlyDue`, `sentOnTimeAtDue`, `sentLateOneSecondAfter`, `sentStatusIgnoresNow`.
- [ ] **Step 2:** `./mvnw clean test -Dtest=DeadlinesTest` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(reporting): CRA deadlines and their status`.

### Task 2 [Alumno]: Revisar hallazgos y abrir eventos (`V11`)

**Lección previa:** máquina de estados en una entidad, transacciones que crean varias filas, 409 para transiciones inválidas.

**Files:**
- Create: `api/src/main/resources/db/migration/V11__reporting.sql`
- Create en `reporting/`: `ReportableEvent.java`, `Report.java`, `DraftStatus.java`, repositorios, `ReviewService.java`, `EventService.java`, `ReportingController.java`, DTOs (`DismissRequest`, `EventResponse`, `ReportSummary`), excepciones (`FindingNotReviewableException` 409, `EventNotFoundException` 404)
- Modify: `vulns/Finding.java` (`confirm(Instant, UUID)`, `dismiss(Instant, UUID, String)`, campos de revisión), `vulns/FindingStatus.java`, `vulns/VulnsService.java` (la lista de hallazgos admite `?status=`)
- Test: `api/src/test/java/io/cradesk/api/reporting/ReviewApiIT.java`

**Interfaces:**
- `V11`: `findings` cambia su `CHECK` a `('PENDING_REVIEW','CONFIRMED','DISMISSED')` y suma `reviewed_at`, `reviewed_by`, `dismiss_reason varchar(1000)`; tabla `reportable_events` (`id`, `organization_id`, `finding_id` único, `product_version_id`, `cve_id`, `confirmed_by`, `confirmed_at`); tabla `reports` (`id`, `event_id`, `type`, único `(event_id, type)`, `due_at`, `draft_status`, `content text` (JSON), `generated_by varchar(20)`, `approved_by`, `approved_at`, `sent_at`, `sent_by`, `enisa_reference varchar(100)`, `updated_at`); tabla `ai_usage` (`organization_id`, `day date`, `count int`, pk de ambos). Restricciones con nombre.
- `POST /api/findings/{id}/confirm` → 201 `EventResponse(UUID id, UUID findingId, String productName, String version, String cveId, Instant confirmedAt, List<ReportSummary> reports)`; `ReportSummary(ReportType type, Instant dueAt, DeadlineStatus status, DraftStatus draftStatus, Instant sentAt, String enisaReference)`.
- `POST /api/findings/{id}/dismiss` `{reason}` → 200; hallazgo ya revisado → 409; motivo corto o vacío → 400 con `errors.reason`.
- `GET /api/events` → eventos de la empresa (más recientes primero) con sus relojes; `GET /api/events/{id}` → 200 · 404.
- `GET /api/findings?status=PENDING_REVIEW|CONFIRMED|DISMISSED` (por defecto `PENDING_REVIEW`).

- [ ] **Step 1 (Claude):** `ReviewApiIT` (con hallazgo creado por el escáner y los dobles de OSV/KEV): `confirmOpensEventWithThreeClocks` (vencimientos exactos 24 h/72 h/14 d con el `Clock` fijo de test), `dismissNeedsAReason`, `dismissStoresReasonAndHidesFromPending`, `reviewingTwiceConflicts`, `otherTenantsFindingIsNotFound`, `demoCannotReview`, `eventsListShowsClockStatus`.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(reporting): review findings and open reportable events (V11)`.

### Task 3 [Claude]: Generadores de borradores (plantilla, Claude, compatible con OpenAI)

**Files:**
- Modify: `api/pom.xml` (`com.anthropic:anthropic-java`)
- Create en `reporting/draft/`: `DraftGenerator.java`, `DraftContext.java`, `DraftContent.java` (records por tipo), `DraftPrompt.java`, `TemplateDraftGenerator.java`, `ClaudeDraftGenerator.java`, `OpenAiCompatibleDraftGenerator.java`, `AiProperties.java`, `DraftGeneratorConfig.java`, `DraftGenerationException.java`
- Test: `api/src/test/java/io/cradesk/api/reporting/draft/TemplateDraftGeneratorTest.java`, `OpenAiCompatibleDraftGeneratorTest.java` (con `MockRestServiceServer`), `ClaudeDraftGeneratorTest.java` (con un `HttpServer` local del JDK como API falsa), `DraftPromptTest.java`

**Interfaces:**
- `interface DraftGenerator { String name(); DraftContent generate(ReportType type, DraftContext context); }`.
- `record DraftContext(String organizationName, String productName, String productClass, String version, String cveId, String vulnerabilityId, String summary, String severity, LocalDate kevDateAdded, String kevVendorProject, String kevProduct, List<String> affectedComponents, Instant confirmedAt)`.
- `sealed interface DraftContent` con `record EarlyWarningDraft(...)`, `record NotificationDraft(...)`, `record FinalReportDraft(...)` y los campos de la tabla; `String toJson()` / `static DraftContent fromJson(ReportType, String)`.
- `TemplateDraftGenerator` (siempre disponible, determinista: rellena con los datos y `TO BE COMPLETED`).
- `ClaudeDraftGenerator`: `AnthropicOkHttpClient` con `ANTHROPIC_API_KEY` (y `baseUrl` configurable para test), `MessageCreateParams.builder().model("claude-haiku-5-5").maxTokens(4000L).outputConfig(<record>.class)` con efforts `LOW`; comprueba `stopReason` (refusal o max_tokens → `DraftGenerationException`).
- `OpenAiCompatibleDraftGenerator`: `RestClient` con timeouts, `POST {baseUrl}/chat/completions` `{model, messages:[system,user], response_format:{type:"json_object"}, temperature: 0.2}`, lee `choices[0].message.content` y lo valida contra el record.
- `DraftPrompt.system(ReportType)` y `DraftPrompt.user(DraftContext)`: los datos del cliente van dentro de un bloque `<product_data>` con el aviso de que son datos, no instrucciones.
- Selección por `CRADESK_AI_PROVIDER` (`template` por defecto); si el proveedor elegido no tiene clave, la app arranca con un aviso y usa la plantilla.

- [ ] **Step 1:** Tests: plantilla por tipo con `TO BE COMPLETED`; OpenAI-compatible (envía modelo, `response_format`, `Authorization: Bearer`; parsea el JSON; 401, timeout simulado y JSON inválido → `DraftGenerationException`); Claude (la API falsa recibe `model` `claude-haiku-5-5` y responde un mensaje con el JSON; `refusal` → excepción); el prompt de usuario contiene el bloque de datos y un nombre de componente con "ignore previous instructions" queda dentro del bloque.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(reporting): pluggable draft generators (template, Claude Haiku, OpenAI-compatible)`.

### Task 4 [Alumno]: Borradores, aprobación, envío y exportación

**Files:**
- Create en `reporting/`: `ReportService.java`, `AiQuota.java`, `ReportResponse.java`, `UpdateReportRequest.java`, `MarkSentRequest.java`, excepciones (`DailyAiLimitException` 429 → base nueva `common/TooManyRequestsException`, `ReportNotApprovedException` 409, `ReportAlreadySentException` 409)
- Modify: `ReportingController.java`, `common/ApiExceptionHandler.java`
- Test: `api/src/test/java/io/cradesk/api/reporting/ReportApiIT.java`

**Interfaces:**
- `POST /api/events/{id}/reports/{type}/draft` → 200 `ReportResponse(ReportType type, Instant dueAt, DeadlineStatus status, DraftStatus draftStatus, JsonNode content, String generatedBy, String aiError, Instant approvedAt, Instant sentAt, String enisaReference)`. Con un proveedor de IA: consume cupo (429 si se acabó); si la IA falla, plantilla + `aiError`. Reemplaza el borrador si no está enviado (409 si lo está).
- `PUT /api/events/{id}/reports/{type}` `{content}` → valida contra los campos del tipo (400 con `errors`), `DRAFTED`.
- `POST …/approve` → `APPROVED` (409 si está vacío o enviado).
- `POST …/sent` `{sentAt?, enisaReference?}` → exige `APPROVED`; `sentAt` futuro → 400; detiene el reloj.
- `GET …/export?format=json|markdown` → archivo `<producto>-<versión>-<cve>-<tipo>.{json,md}`; solo aprobado o enviado (409 si no).
- `AiQuota.tryConsume(UUID organizationId)` → `boolean`, atómico en SQL.

- [ ] **Step 1 (Claude):** `ReportApiIT` con un doble de `DraftGenerator` programable: `draftWithTemplateDoesNotUseQuota`, `aiDraftConsumesQuota`, `aiFailureFallsBackToTemplateWithReason`, `dailyLimitReturns429`, `concurrentRequestsRespectTheLimit` (20 hilos, cupo restante 2 → 2 pasan), `editRevertsApprovalToDrafted`, `invalidContentReturns400`, `sentRequiresApproval`, `sentStopsTheClockOnTimeAndLate`, `futureSentAtReturns400`, `sentIsFinal`, `exportJsonAndMarkdown`, `otherTenantsReportIsNotFound`, `demoCannotDraftOrApprove`.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(reporting): drafts with daily AI quota, approval, sent to ENISA and export`.

### Task 5 [Claude]: Auditoría con cadena de hashes (`V12`)

**Files:**
- Create: `api/src/main/resources/db/migration/V12__audit.sql`
- Create en `api/src/main/java/io/cradesk/api/audit/`: `AuditEntry.java`, `AuditChainHead.java`, repositorios, `AuditService.java`, `AuditHasher.java`, `AuditController.java`, `AuditEntryResponse.java`, `AuditVerification.java`
- Modify: `ReviewService`, `ReportService`, `SbomService`, `ApiKeyService` (registran en su transacción)
- Test: `api/src/test/java/io/cradesk/api/audit/AuditHasherTest.java`, `AuditApiIT.java`

**Interfaces:**
- `V12`: `audit_chain_heads (organization_id pk, last_seq bigint, last_hash varchar(64))`; `audit_entries (id, organization_id, seq, occurred_at, actor_id, actor_role, action varchar(60), target_type varchar(40), target_id varchar(100), details text, prev_hash varchar(64), hash varchar(64), único (organization_id, seq))`; función y trigger `audit_entries_append_only` que lanzan error en `UPDATE`/`DELETE`.
- `AuditService.record(String action, String targetType, String targetId, Map<String, Object> details)`: usa el principal actual (o `system`), bloquea la cabeza de la cadena, calcula el hash y guarda; debe llamarse dentro de una transacción (si no hay, lanza `IllegalStateException`).
- `AuditHasher.hash(String prevHash, long seq, Instant at, String actor, String action, String targetType, String targetId, String canonicalDetails)` con JSON de `details` ordenado por clave.
- `GET /api/audit` → página de entradas (más reciente primero); `GET /api/audit/verify` → `AuditVerification(boolean valid, long entries, Long firstInvalidSeq)`.

- [ ] **Step 1:** Tests: `AuditHasherTest` (determinista; cambia si cambia cualquier campo; el orden de las claves de `details` no cambia el hash); `AuditApiIT`: `actionsAreRecordedInOrder` (confirmar, generar, aprobar, enviar → 4 entradas con `seq` 1..4 y cada `prev_hash` igual al `hash` anterior), `verifyIsValid`, `tamperedEntryIsDetected` (en el test se desactiva el trigger, se cambia `details` y `verify` devuelve `firstInvalidSeq`), `updateAndDeleteAreRejected`, `concurrentActionsKeepTheChain` (10 hilos), `eachOrganizationHasItsOwnChain`, `failedActionLeavesNoAuditEntry`.
- [ ] **Step 2:** `./mvnw clean verify` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `./mvnw clean verify` → PASS.
- [ ] **Step 5:** Commit `feat(audit): hash-chained append-only audit log per organization (V12)`.

### Task 6 [Claude]: Demo, README y cierre

**Files:** `demo/DemoAccountInitializer.java` (evento confirmado sobre el hallazgo Log4Shell de la demo, tres borradores pregenerados desde un recurso JSON, el aviso temprano marcado enviado, y sus entradas de auditoría), `README.md`, `scripts/demo.sh`, `.env.example` (`CRADESK_AI_PROVIDER`, `CRADESK_AI_BASE_URL`, `CRADESK_AI_MODEL`, `CRADESK_AI_API_KEY`, `ANTHROPIC_API_KEY`, `CRADESK_AI_DAILY_LIMIT`)

- [ ] **Step 1:** `demo.sh`: tras los hallazgos, confirmar el de Log4Shell, ver los relojes, pedir el borrador del aviso temprano, aprobarlo, marcarlo enviado con referencia, exportarlo y verificar la auditoría.
- [ ] **Step 2:** Correr `demo.sh` con `CRADESK_AI_PROVIDER=template`, y otra vez con el proveedor real que el usuario haya puesto en su `.env` (DeepSeek), si lo puso. Si falta la clave, se prueba solo la plantilla y se informa.
- [ ] **Step 3:** Revisión de toda la rama con un revisor independiente y corrección de lo crítico e importante con TDD.
- [ ] **Step 4:** Commit `docs: stage 6 README and demo`.
- [ ] **Step 5:** Dejar `main` actualizado localmente y darle al usuario los comandos exactos para el push. No hacer push.

---

**Fuera de alcance de esta etapa:**
- Exportación PDF (spec §6: primer recorte).
- Envío directo a ENISA (spec §2: no hay API pública confirmada).
- Notificaciones por email/Slack (no-meta).
- Reabrir un hallazgo descartado o cerrar un evento a mano: después de diciembre.
