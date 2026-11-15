# CRA Desk — Etapa 2: `identity` y seguridad — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Modo de trabajo:** el usuario elige cada día entre **tutor** y **avance directo**. En modo tutor:
> - las tareas **[Alumno]** siguen este ciclo: lección corta con un ejemplo de otro dominio (`Book`), Claude escribe el test, el alumno escribe el código, Claude revisa con pistas progresivas y el alumno anota 3 líneas en `docs/aprendizaje.md`;
> - las tareas **[Claude]** (configuración e infraestructura) las hace Claude y luego las explica.
>
> En avance directo, Claude implementa todo con TDD y al final indica qué archivos leer.

**Goal:** Cada empresa ve y modifica solo sus propios datos. Se entra con sesión (cookie HttpOnly), por invitación, con la cuenta demo de solo lectura o con una clave de API para la CLI. Hay además una lista de espera pública.

**Architecture:** Spring Security dentro del monolito. Un `SecurityFilterChain` combina tres formas de autenticación: sesión con login JSON, clave de API (`Authorization: Bearer crd_…`) y token de administración (`X-Admin-Token`). Las tres producen el mismo principal, `TenantPrincipal`, y el catálogo filtra cada consulta por `organization_id` a través de `CurrentTenant`. Los errores 401 y 403 también son `ProblemDetail`.

**Tech Stack:** el de las etapas 0 y 1 más `spring-boot-starter-security`, `spring-boot-starter-security-test` (test), BCrypt (`PasswordEncoderFactories`) y SHA-256 (`MessageDigest`).

**Spec:** `docs/superpowers/specs/2026-11-13-cra-desk-design.md` (secciones 2, 5 y 7; etapa 2 de la tabla de la sección 6).

## Global Constraints

- Código, identificadores, mensajes de la API y README en inglés. Explicaciones y `docs/aprendizaje.md` en español.
- Un paquete por módulo: `io.cradesk.api.identity` (cuentas, seguridad, claves, invitaciones) y `io.cradesk.api.waitlist`. Las excepciones compartidas van en `io.cradesk.api.common`.
- Todos los errores salen como `ProblemDetail` (RFC 9457), incluidos 401, 403 y 500. Un 500 nunca muestra la traza ni el mensaje interno.
- Secretos solo en variables de entorno. Nunca se guarda en texto plano ni se escribe en logs una contraseña, una clave de API, un código de invitación o el token de administración.
- Un usuario pertenece a una sola empresa. No hay invitaciones de equipo ni varios roles por empresa (spec §7).
- La demo de solo lectura no llama a la IA y no puede modificar nada (spec §2).
- Maven wrapper fijado en 3.9.16. No se cambia.
- Tests de integración `*IT.java` con `./mvnw clean verify`.
- Migraciones nuevas: nunca se edita una migración ya aplicada (`V1`). Todas las restricciones nuevas llevan nombre explícito.

## Decisiones de diseño de esta etapa (a criterio de quien implementa, spec §7)

| Tema | Decisión | Por qué |
|---|---|---|
| Sesión | `HttpSession` en memoria, cookie `JSESSIONID` con `HttpOnly`, `SameSite=Lax`, `Secure` configurable (`true` en producción), expira a las 8 h. | Railway corre una sola instancia. Que un despliegue cierre las sesiones es aceptable en la demo. Spring Session JDBC queda para la Etapa 8 si hace falta. |
| CSRF | Activado para las peticiones con sesión, al estilo SPA: cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`; `GET /api/auth/csrf` entrega el token. Se exime a las peticiones con `Authorization: Bearer` o `X-Admin-Token`. | Con cookies de sesión, CSRF es obligatorio. Un navegador nunca envía esas cabeceras por su cuenta, así que eximirlas es seguro. |
| Contraseñas | BCrypt vía `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. Mínimo 12 caracteres, máximo 72 **bytes** en UTF-8. | BCrypt ignora o rechaza lo que pasa de 72 bytes, y un carácter no ASCII ocupa más de un byte. |
| Fuerza bruta | 5 fallos seguidos bloquean la cuenta 15 minutos. Mientras está bloqueada, incluso la contraseña correcta da 401. | Protección mínima sin infraestructura extra. |
| Login | El email desconocido y la contraseña incorrecta dan el mismo 401 (`"Invalid email or password"`). Con un email desconocido igual se compara contra un hash ficticio. | Así no se puede averiguar qué emails tienen cuenta, ni por el mensaje ni por el tiempo de respuesta. |
| Emails | Se guardan recortados y en minúsculas, con índice único sobre `lower(email)`. | `" Alice@X.com "` y `"alice@x.com"` son la misma persona. |
| Roles | `enum AccessRole { MEMBER, DEMO, CLI }`. Un usuario es `MEMBER` o `DEMO`; una clave de API actúa como `CLI` de su empresa. | Tres reglas simples de autorización (tabla de la Tarea 3). |
| Invitaciones | Las crea el administrador de la plataforma con `POST /api/admin/invitations` y la cabecera `X-Admin-Token` (variable `CRADESK_ADMIN_TOKEN`, mínimo 32 caracteres; si está vacía, los endpoints de admin dan siempre 401). El código es de un solo uso, está ligado al email, expira a los 7 días y se guarda como SHA-256. Al aceptarlo se crean la empresa y el usuario. | Cumple "código o comando de admin" (spec §2) sin construir un panel de administración. |
| Claves de API | Formato `crd_<prefix 8>_<secreto 32>`, generadas con `SecureRandom` en base64url. Se guarda el prefijo (para buscar) y el SHA-256 de la clave completa. Se muestran una sola vez. Se revocan, no se borran. | La clave tiene entropía alta, así que SHA-256 basta: BCrypt solo hace falta para contraseñas elegidas por personas. El prefijo permite identificarla sin exponerla. |
| Demo | Organización "Demo Manufacturer (read-only)" y usuario `demo@cradesk.invalid` sin contraseña. Los crea `DemoAccountInitializer` al arrancar si `cradesk.demo.enabled=true`. `POST /api/auth/demo` abre la sesión demo. | El botón "Probar demo" no necesita credenciales. `.invalid` es un dominio reservado (RFC 2606), así que no apunta a nadie real. |
| Lista de espera | `POST /api/access-requests` público. Con datos válidos responde **202 siempre**, aunque el email ya esté en la lista. | Una respuesta distinta revelaría quién ya se apuntó. |
| Aislamiento | Un id de otra empresa da **404**, nunca 403. | Un 403 confirmaría que ese id existe. |
| `product_versions` | **No** recibe `organization_id`: siempre se llega a una versión a través de su producto, que sí está filtrado por empresa. | Evita duplicar datos. La Etapa 3 sube SBOM por producto y versión, pasando por `ProductService.find`. |
| Migraciones | `V2__identity.sql` (empresas y usuarios), `V3__catalog_tenancy.sql`, `V4__invitations.sql`, `V5__api_keys.sql`, `V6__access_requests.sql`, `V7__product_row_version.sql`. | Cada tarea trae su propia migración y queda en verde por sí sola. **Cambio respecto de lo anotado antes:** `organization_id` en el catálogo va en `V3`, no en `V2`, porque necesita que exista la tabla `organizations`. |

## Review Focus

1. **Acceso entre empresas por id:** `GET`/`PUT`/`DELETE` de un producto, sus versiones o una versión concreta de otra empresa, por sesión o con una clave de API de otra empresa, debe dar 404, y la lista solo muestra lo propio. Lo cubre `TenantIsolationIT` (Tarea 4) y un test de `ApiKeyApiIT` (Tarea 7).
2. **Login:** el email desconocido y la contraseña incorrecta dan exactamente el mismo cuerpo 401. Tras 5 fallos, la contraseña correcta también da 401 hasta que pasa el bloqueo (Tarea 3).
3. **Escrituras de la demo:** `POST`/`PUT`/`DELETE` del catálogo y `POST /api/api-keys` con la sesión demo deben dar 403 `ProblemDetail`; las lecturas dan 200 (Tarea 6).
4. **Secretos que se filtran:** la clave de API y el código de invitación aparecen solo en la respuesta 201. Los `GET` no los devuelven y la BD guarda solo hashes. Una clave revocada da 401 (Tareas 5 y 7).
5. **Mayúsculas y espacios en el email:** `" Alice@Example.com "` sirve para iniciar sesión como `alice@example.com`, choca con una invitación ya existente para ese email y no duplica la lista de espera (Tareas 3, 5 y 8).

---

### Task 1 [Claude]: Base de errores compartida

Cierra hallazgos de la revisión de las etapas 0 y 1: `common` dependía de `catalog`, los 500 no pasaban por el handler y un `productClass` desconocido no indicaba qué campo fallaba.

**Files:**
- Create: `api/src/main/java/io/cradesk/api/common/NotFoundException.java`, `ConflictException.java`
- Modify: las 5 excepciones de `catalog` (extienden la base que corresponda); `ApiExceptionHandler.java`
- Test: `api/src/test/java/io/cradesk/api/common/ApiExceptionHandlerIT.java`; `ProductApiIT.unknownProductClassReturns400ProblemDetail`

**Interfaces:**
- Produces:
  - `abstract class NotFoundException extends RuntimeException` y `abstract class ConflictException extends RuntimeException`, con constructor `(String message)`.
  - `ApiExceptionHandler` maneja `NotFoundException` → 404 y `ConflictException` → 409, y ya no importa nada de `catalog`. Las tareas siguientes solo extienden estas bases.
  - Cualquier otra excepción → 500, con `title` `"Internal error"` y `detail` `"Unexpected error"`. Se registra en el log con su traza.
  - Un enum o valor ilegible en el JSON → 400 con `errors.<campo>`, usando `InvalidFormatException.getPath()`.

- [ ] **Step 1:** Escribir los tests:
  - `ApiExceptionHandlerIT.unexpectedErrorReturns500ProblemWithoutInternals`: usa un `@RestController` de test (en `src/test`) que lanza `IllegalStateException("secret internal detail")`. Espera status 500, content type `application/problem+json`, `$.detail` = `"Unexpected error"` y que el cuerpo **no contenga** `"secret internal detail"`.
  - Ampliar `unknownProductClassReturns400ProblemDetail` con `jsonPath("$.errors.productClass").exists()`.
- [ ] **Step 2:** Ejecutar `cd api && ./mvnw clean verify`. Resultado esperado: FAIL en esos dos tests.
- [ ] **Step 3:** Implementar las bases, cambiar el `extends` de las excepciones de `catalog`, agregar el `@ExceptionHandler(Exception.class)` y sobrescribir `handleHttpMessageNotReadable`.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Commit con el mensaje `refactor(common): base not-found/conflict exceptions, 500 and enum errors as problem details`.

### Task 2 [Alumno]: Empresas y usuarios (`V2`)

**Lección previa:** relación muchos-a-uno sin cascada (repaso), `enum` guardado como texto, `CHECK` en SQL e índices únicos funcionales (`lower(email)`).

**Files:**
- Create: `api/src/main/resources/db/migration/V2__identity.sql`
- Create en `api/src/main/java/io/cradesk/api/identity/`: `Organization.java`, `OrganizationRepository.java`, `UserAccount.java`, `UserAccountRepository.java`, `AccessRole.java`
- Test: `api/src/test/java/io/cradesk/api/identity/UserAccountRepositoryIT.java`

**Interfaces:**
- Produces:
  - Tablas:
    - `organizations`: `id uuid pk`, `name varchar(120) not null`, `created_at timestamptz not null`.
    - `users`: `id uuid pk`, `organization_id uuid not null` (FK con nombre `users_organization_fk`), `email varchar(254) not null`, índice único `users_email_unique ON users (lower(email))`, `password_hash varchar(100)` (nullable: la demo no tiene contraseña), `role varchar(16) not null` con `CHECK (role IN ('MEMBER','DEMO'))`, `failed_login_attempts int not null default 0`, `locked_until timestamptz`, `created_at timestamptz not null`.
  - `enum AccessRole { MEMBER, DEMO, CLI }`.
  - Entidad `Organization(String name)` con `UUID id` e `Instant createdAt`.
  - Entidad `UserAccount` (tabla `users`). Constructor `UserAccount(Organization organization, String email, String passwordHash, AccessRole role)`; el constructor guarda el email recortado y en minúsculas. Métodos `boolean isLockedAt(Instant now)`, `void recordFailedLogin(Instant now)` (al quinto fallo fija `lockedUntil = now + 15 min`) y `void recordSuccessfulLogin()` (vuelve el contador a 0).
  - `OrganizationRepository extends JpaRepository<Organization, UUID>` con `Optional<Organization> findByName(String name)`.
  - `UserAccountRepository extends JpaRepository<UserAccount, UUID>` con `Optional<UserAccount> findByEmail(String normalizedEmail)` y `boolean existsByEmail(String normalizedEmail)`. Quien llama ya pasa el email normalizado.
  - Timestamps truncados a microsegundos, como en `catalog`.

- [ ] **Step 1 (Claude):** Escribir `UserAccountRepositoryIT` con estos tests:
  - `savesUserWithNormalizedEmail`: `new UserAccount(org, " Alice@Example.com ", "x", MEMBER)` se guarda con `email` = `"alice@example.com"`.
  - `duplicateEmailIgnoringCaseIsRejected`: un segundo usuario `"ALICE@example.com"` → `DataIntegrityViolationException` y `Constraints.isViolated(e, UserAccount.EMAIL_UNIQUE)`.
  - `fifthFailedLoginLocksForFifteenMinutes`: 4 fallos → `isLockedAt(now)` es `false`; el quinto → `true` en `now + 14 min` y `false` en `now + 16 min`.
  - `successfulLoginResetsCounter`.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify -Dit.test=UserAccountRepositoryIT`. Resultado esperado: FAIL por compilación.
- [ ] **Step 3 (Alumno):** Escribir la migración, el enum, las dos entidades y los repositorios. Cuidado con los errores que ya cometiste: nada de Java dentro del `.sql`, comas y `;`, y que los nombres de campo en los métodos del repositorio existan.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit con el mensaje `feat(identity): organizations and users (V2)`.

### Task 3 [Claude]: Spring Security, sesión y login

**Lección (explicación de Claude al terminar):**
- la cadena de filtros de Spring Security;
- `Authentication` y `SecurityContext`;
- por qué un login JSON debe guardar el contexto en la sesión y cambiar el id de sesión (session fixation);
- qué es CSRF y por qué se exime a las peticiones con `Bearer`.

**Files:**
- Modify: `api/pom.xml` (`spring-boot-starter-security`, `spring-boot-starter-security-test`), `application.yml`
- Create en `identity/`: `SecurityConfig.java`, `ProblemDetailAuthHandlers.java` (entry point 401 y access-denied 403), `TenantPrincipal.java`, `CurrentTenant.java`, `AuthController.java`, `AuthService.java`, `LoginRequest.java`, `MeResponse.java`, `Emails.java`
- Create: `api/src/test/java/io/cradesk/api/support/TestTenants.java` (lo importa `@IntegrationTest`)
- Modify: `ProductApiIT`, `ProductVersionApiIT`, `ProductTimestampsIT` (cada petición lleva `.with(tenant.member())`)
- Test: `api/src/test/java/io/cradesk/api/identity/AuthApiIT.java`, `SessionCookieIT.java`

**Interfaces:**
- Consumes: `UserAccount`, `UserAccountRepository`, `AccessRole` (Tarea 2) y las excepciones base (Tarea 1).
- Produces:
  - `record TenantPrincipal(UUID organizationId, UUID userId, String email, AccessRole role)`. Es el principal de toda `Authentication`, con la autoridad `"ROLE_" + role`.
  - `@Component CurrentTenant` con `UUID organizationId()` y `TenantPrincipal principal()`. Lanza `IllegalStateException` si no hay autenticación (no debería pasar detrás de la seguridad).
  - `final class Emails` con `static String normalize(String raw)` (recorta y pasa a minúsculas en `Locale.ROOT`).
  - `AuthService.login(LoginRequest, HttpServletRequest, HttpServletResponse)`: autentica, cambia el id de sesión y guarda el contexto con `HttpSessionSecurityContextRepository`. Lo reutilizan la Tarea 5 (aceptar invitación) y la Tarea 6 (demo) mediante `void startSession(TenantPrincipal, HttpServletRequest, HttpServletResponse)`.
  - Endpoints:
    - `GET /api/auth/csrf` → 200 `{"headerName","token"}`
    - `POST /api/auth/login` `{email, password}` → 204, abre la sesión · 400 validación · 401 `"Invalid email or password"`
    - `POST /api/auth/logout` → 204, invalida la sesión
    - `GET /api/auth/me` → 200 `MeResponse(UUID userId, String email, UUID organizationId, String organizationName, AccessRole role)` · 401
  - Reglas de autorización, en este orden:

    | Ruta | Acceso |
    |---|---|
    | `GET /actuator/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/demo`, `POST /api/auth/accept-invitation`, `POST /api/access-requests` | público |
    | `/api/admin/**` | autoridad `ROLE_PLATFORM_ADMIN` (Tarea 5) |
    | `/api/api-keys/**` | `MEMBER` |
    | `GET /api/auth/me`, `POST /api/auth/logout` | autenticado |
    | `GET /api/**` | `MEMBER`, `DEMO`, `CLI` |
    | resto de `/api/**` | `MEMBER`, `CLI` |
    | cualquier otra ruta | denegado |

  - `application.yml`: `server.servlet.session.timeout: 8h`; cookie `http-only: true`, `same-site: lax`, `secure: ${SESSION_COOKIE_SECURE:false}`.
  - CSRF: Spring Security 7 trae `http.csrf(c -> c.spa())`. Si no existe en la versión instalada, usar la receta SPA de la documentación de referencia (`CookieCsrfTokenRepository.withHttpOnlyFalse()` y el handler SPA). Las peticiones con cabecera `Authorization: Bearer` o `X-Admin-Token` quedan exentas.
  - `TestTenants` (test): `TestTenant create(String orgName)` guarda una empresa y un usuario `MEMBER` con email `<org en minúsculas sin espacios>@example.com` y contraseña `"correct-horse-battery"`. Devuelve `record TestTenant(UUID organizationId, UUID userId, String email, String password, RequestPostProcessor member)`, donde `member` aplica `authentication(...)` con su `TenantPrincipal` más `csrf()`.

- [ ] **Step 1:** Escribir `AuthApiIT` con estos tests:
  - `loginWithCorrectPasswordOpensSession`: login → 204; con la `MockHttpSession` devuelta, `GET /api/auth/me` → 200 con `email` y `organizationName`.
  - `loginNormalizesEmail`: `" Alice@Example.com "` entra como `alice@example.com`.
  - `wrongPasswordAndUnknownEmailGiveIdenticalResponse`: los dos 401 tienen exactamente el mismo cuerpo.
  - `fiveFailuresLockTheAccount`: 5 fallos y luego la contraseña correcta → 401.
  - `meWithoutSessionReturns401ProblemDetail`: content type `application/problem+json`.
  - `catalogRequiresAuthentication`: `GET /api/products` sin sesión → 401.
  - `postWithoutCsrfTokenReturns403`: sesión válida, `POST /api/products` sin `csrf()` → 403 `ProblemDetail`.
  - `logoutEndsSession`: después de logout, `/api/auth/me` con la misma sesión → 401.
- [ ] **Step 2:** Escribir `SessionCookieIT` con `@SpringBootTest(webEnvironment = RANDOM_PORT)` y `RestClient` real: `sessionCookieIsHttpOnlyAndSameSiteLax` hace login (pidiendo antes el token CSRF) y comprueba que `Set-Cookie` de `JSESSIONID` contiene `HttpOnly` y `SameSite=Lax`.
- [ ] **Step 3:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 4:** Implementar la configuración, los handlers, el servicio y el controller. Agregar `TestTenants` y añadir `.with(tenant.member())` en las peticiones de los tests del catálogo.
- [ ] **Step 5:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 6:** Commit con el mensaje `feat(identity): spring security with session login, csrf and problem-detail 401/403`.

### Task 4 [Alumno]: Catálogo por empresa (`V3`) y tests de aislamiento

**Lección previa:** multi-tenancy por columna; por qué filtrar en el repositorio y no en el controller; por qué 404 y no 403.

**Files:**
- Create: `api/src/main/resources/db/migration/V3__catalog_tenancy.sql`
- Modify: `Product.java`, `ProductRepository.java`, `ProductService.java`, `ProductVersion.java` (constante del nombre), `CatalogConstraintsIT.java`, `ProductRepositoryIT.java` (nuevo constructor y `existsInOrganization`)
- Test: `api/src/test/java/io/cradesk/api/catalog/TenantIsolationIT.java`

**Interfaces:**
- Consumes: `CurrentTenant.organizationId()` (Tarea 3) y `TestTenants` (Tarea 3).
- Produces:
  - `V3__catalog_tenancy.sql`:
    1. `ALTER TABLE products ADD COLUMN organization_id uuid`, con FK `products_organization_fk`.
    2. Si ya hay productos (datos locales de antes), insertar la empresa `00000000-0000-0000-0000-000000000001` llamada `'Unassigned (pre-V3 data)'` y asignársela: `INSERT … SELECT … WHERE EXISTS (SELECT 1 FROM products)`, seguido de un `UPDATE`.
    3. `SET NOT NULL`.
    4. `DROP INDEX products_name_unique` y crear `products_org_name_unique ON products (organization_id, lower(name))`.
    5. `ALTER TABLE product_versions RENAME CONSTRAINT product_versions_product_id_version_key TO product_versions_product_version_unique`.
  - `Product` recibe `UUID organizationId` en el constructor: `Product(UUID organizationId, String name, String description, ProductClass productClass)`. Las constantes pasan a ser `Product.NAME_UNIQUE = "products_org_name_unique"` y `ProductVersion.VERSION_UNIQUE = "product_versions_product_version_unique"`.
  - `ProductRepository`:
    - `Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId)`
    - `List<Product> findAllByOrganizationId(UUID organizationId, Sort sort)`
    - `existsByNameIgnoreCase` se convierte en `boolean existsInOrganization(UUID organizationId, String name)`, con `@Query` usando `lower()`.
  - `ProductService.find(UUID id)` usa `findByIdAndOrganizationId(id, tenant.organizationId())`. Sigue siendo el **único** camino para obtener un producto.

- [ ] **Step 1 (Claude):** Escribir `TenantIsolationIT`. Crea las empresas `acme` y `globex` con `TestTenants`; Globex crea "Smart Lock" con la versión `1.0.0`. Tests:
  - `otherTenantsProductIsNotFound`: Acme hace `GET`, `PUT` y `DELETE` del producto de Globex → 404.
  - `otherTenantsVersionsAreNotFound`: Acme hace `GET` de `/versions`, `POST /versions` y `GET /versions/{versionId}` → 404.
  - `listShowsOnlyOwnProducts`: la lista de Acme tiene longitud 0.
  - `sameNameAllowedInDifferentTenants`: Acme crea "Smart Lock" → 201.
  - `duplicateNameInSameTenantStillConflicts`: Globex crea "smart lock" → 409.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3 (Alumno):** Escribir la migración, agregar `organizationId` a `Product`, cambiar los métodos del repositorio y hacer que el servicio use `CurrentTenant`. Actualizar `CatalogConstraintsIT` para que cree antes una empresa.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit con el mensaje `feat(catalog): scope products by organization (V3) with isolation tests`.

### Task 5 [Alumno]: Invitaciones y token de administración (`V4`)

**Lección previa:** generar secretos con `SecureRandom`; hash SHA-256 contra BCrypt (cuándo usar cada uno); comparación en tiempo constante (`MessageDigest.isEqual`); `OncePerRequestFilter`.

**Files:**
- Create: `api/src/main/resources/db/migration/V4__invitations.sql`
- Create en `identity/`: `Invitation.java`, `InvitationRepository.java`, `InvitationService.java`, `InvitationController.java` (admin), `CreateInvitationRequest.java`, `InvitationCreatedResponse.java`, `AcceptInvitationRequest.java`, `AdminTokenFilter.java`, `AdminProperties.java`, `SecretTokens.java`, `InvalidInvitationException.java`, `EmailAlreadyRegisteredException.java`
- Modify: `SecurityConfig.java`, `AuthController.java`, `application.yml` (`cradesk.admin.token: ${CRADESK_ADMIN_TOKEN:}`), `.env.example`
- Test: `api/src/test/java/io/cradesk/api/identity/InvitationApiIT.java`

**Interfaces:**
- Consumes: `AuthService.startSession(...)`, `Emails.normalize`, `Organization`/`UserAccount` y sus repositorios.
- Produces:
  - Tabla `invitations`: `id uuid pk`, `email varchar(254) not null`, `code_hash char(64) not null` con único `invitations_code_hash_unique`, `expires_at timestamptz not null`, `accepted_at timestamptz`, `created_at timestamptz not null`.
  - `final class SecretTokens` con `static String random(int bytes)` (base64url sin relleno) y `static String sha256Hex(String value)`. La Tarea 7 los reutiliza.
  - `AdminTokenFilter`: si `X-Admin-Token` coincide en tiempo constante con `cradesk.admin.token`, autentica con la autoridad `ROLE_PLATFORM_ADMIN`. Si el token configurado está vacío, nunca autentica. Si está configurado con menos de 32 caracteres, la app no arranca (validación en `AdminProperties`).
  - `POST /api/admin/invitations` `{email}` → 201 `InvitationCreatedResponse(UUID id, String email, Instant expiresAt, String code)` · 409 si el email ya tiene cuenta. El `code` solo aparece aquí.
  - `POST /api/auth/accept-invitation` `AcceptInvitationRequest(@NotBlank String code, @NotBlank @Email String email, @NotBlank @Size(min = 12) String password, @NotBlank @Size(max = 120) String organizationName)`. Crea la empresa y el usuario `MEMBER`, marca `accepted_at`, abre la sesión y responde 201 `MeResponse`.
    - 400 `"Invitation is invalid or expired"` si el código no existe, ya se usó, expiró o el email no coincide (el mismo mensaje en todos los casos);
    - 400 con `errors.password` si la contraseña pasa de 72 bytes en UTF-8;
    - 409 si el email ya tiene cuenta.

- [ ] **Step 1 (Claude):** Escribir `InvitationApiIT` con estas propiedades de test: `cradesk.admin.token` = 40 caracteres fijos.
  - `adminCreatesInvitationAndCodeIsStoredHashed`: 201 con `code`; la fila guardada tiene `code_hash` = `sha256Hex(code)` y ninguna columna contiene `code`.
  - `adminEndpointsRejectMissingOrWrongToken`: sin cabecera → 401; con un token incorrecto → 401.
  - `acceptingCreatesOrganizationUserAndSession`: 201; luego `GET /api/auth/me` con esa sesión → 200 con `organizationName`.
  - `invitationIsSingleUse`: aceptarla otra vez → 400.
  - `expiredInvitationIsRejected`: con `expires_at` en el pasado (se ajusta con `JdbcTemplate`) → 400.
  - `emailMustMatchInvitationIgnoringCase`: la invitación es para `alice@example.com`. Aceptar con `" ALICE@example.com "` → 201; con `bob@example.com` → 400.
  - `inviteForRegisteredEmailConflicts`: → 409.
  - `passwordOver72BytesIsRejected`: `"ñ".repeat(37)` (74 bytes) → 400 con `errors.password`.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3 (Alumno):** Implementar la migración, la entidad, el servicio y los controllers. Claude hace el filtro y la configuración de seguridad, y explica cada parte.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit con el mensaje `feat(identity): admin-issued single-use invitations (V4)`.

### Task 6 [Claude]: Cuenta demo de solo lectura

**Files:**
- Create en `identity/`: `DemoAccountInitializer.java`, `DemoProperties.java`
- Modify: `AuthController.java` (`POST /api/auth/demo`), `application.yml` (`cradesk.demo.enabled: ${CRADESK_DEMO_ENABLED:true}`)
- Test: `api/src/test/java/io/cradesk/api/identity/DemoAccountIT.java`

**Interfaces:**
- Consumes: `AuthService.startSession(...)`, las reglas de la Tarea 3 (`DEMO` solo puede hacer `GET`) y `Product`/`ProductVersion`.
- Produces:
  - `DemoAccountInitializer implements ApplicationRunner`. Es idempotente: si no existe la empresa "Demo Manufacturer (read-only)", la crea junto con el usuario `demo@cradesk.invalid` (`DEMO`, sin contraseña) y dos productos de ejemplo:
    - "Smart Door Lock" (`IMPORTANT_CLASS_I`), versiones `1.0.0` y `1.1.0`;
    - "Home Gateway" (`DEFAULT`), versión `2.3.1`.
  - Los datos de demo completos (SBOM, hallazgos) son de la Etapa 9.
  - `POST /api/auth/demo` → 204 con la sesión demo abierta · 404 si `cradesk.demo.enabled=false`.
  - El usuario demo no puede iniciar sesión con contraseña, porque no tiene hash.

- [ ] **Step 1:** Escribir `DemoAccountIT` con estos tests:
  - `demoLoginSeesSampleProducts`: `POST /api/auth/demo` → 204; `GET /api/products` con esa sesión → 2 productos.
  - `demoCannotWrite`: con la sesión demo y `csrf()`, `POST /api/products`, `PUT` y `DELETE` de un producto demo, `POST /versions` y `POST /api/api-keys` → todos 403 `ProblemDetail`.
  - `initializerIsIdempotent`: ejecutarlo dos veces sigue dejando 1 empresa demo y 2 productos.
  - `demoUserCannotPasswordLogin`: login con `demo@cradesk.invalid` y cualquier contraseña → 401.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Commit con el mensaje `feat(identity): read-only demo account`.

### Task 7 [Alumno]: Claves de API para la CLI (`V5`)

**Lección previa:** el formato prefijo + secreto (como GitHub o Stripe); por qué se revoca y no se borra; un filtro que lee una cabecera.

**Files:**
- Create: `api/src/main/resources/db/migration/V5__api_keys.sql`
- Create en `identity/`: `ApiKey.java`, `ApiKeyRepository.java`, `ApiKeyService.java`, `ApiKeyController.java`, `CreateApiKeyRequest.java`, `ApiKeyCreatedResponse.java`, `ApiKeyResponse.java`, `ApiKeyNotFoundException.java`, `ApiKeyAuthenticationFilter.java`
- Modify: `SecurityConfig.java`
- Test: `api/src/test/java/io/cradesk/api/identity/ApiKeyApiIT.java`

**Interfaces:**
- Consumes: `SecretTokens` (Tarea 5), `CurrentTenant` y `TenantPrincipal` (Tarea 3).
- Produces:
  - Tabla `api_keys`: `id uuid pk`, `organization_id uuid not null` (FK `api_keys_organization_fk`), `created_by uuid not null` (FK `api_keys_user_fk`), `name varchar(80) not null`, `prefix char(8) not null` con único `api_keys_prefix_unique`, `key_hash char(64) not null`, `created_at timestamptz not null`, `last_used_at timestamptz`, `revoked_at timestamptz`.
  - Endpoints (solo `MEMBER` con sesión):
    - `POST /api/api-keys` `{name}` (`@NotBlank @Size(max = 80)`) → 201 `ApiKeyCreatedResponse(UUID id, String name, String prefix, String key, Instant createdAt)`
    - `GET /api/api-keys` → 200 `List<ApiKeyResponse(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt, Instant revokedAt)>`, solo de la propia empresa
    - `DELETE /api/api-keys/{id}` → 204, revoca · 404 si no existe o es de otra empresa
  - `ApiKeyAuthenticationFilter`: con `Authorization: Bearer crd_<prefix>_<secret>`, busca por `prefix` y compara `sha256Hex(clave)` en tiempo constante. Si está activa, autentica como `TenantPrincipal(orgId, createdBy, null, CLI)` y actualiza `last_used_at`. Si la clave es inválida, está revocada o tiene mal formato → 401 `ProblemDetail`, sin pasar al resto de la cadena.

- [ ] **Step 1 (Claude):** Escribir `ApiKeyApiIT` con estos tests:
  - `createdKeyIsShownOnceAndStoredHashed`: el 201 tiene `key` que empieza por `crd_`; el `GET` no tiene campo `key`; en la BD, `key_hash` = `sha256Hex(key)`.
  - `keyAuthenticatesCatalogCalls`: con `Authorization: Bearer <key>` y **sin** `csrf()`, `POST /api/products` → 201 y `GET /api/products` → 200.
  - `keyCannotManageKeys`: con `Bearer`, `POST /api/api-keys` → 403.
  - `revokedKeyIsRejected`: después de `DELETE` → 401.
  - `malformedOrUnknownKeyIsRejected`: `Bearer crd_nope`, una clave bien formada pero inexistente y una que tiene el prefijo correcto con el secreto cambiado → 401.
  - `keyOfOtherTenantCannotSeeProducts`: con la clave de Globex, `GET` del producto de Acme → 404.
  - `cannotRevokeOtherTenantsKey`: → 404.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3 (Alumno):** Implementar la migración, la entidad, el servicio, el controller y el filtro (el filtro con pistas de Claude).
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit con el mensaje `feat(identity): hashed API keys for the CLI (V5)`.

### Task 8 [Alumno]: Lista de espera "Request access" (`V6`)

**Files:**
- Create: `api/src/main/resources/db/migration/V6__access_requests.sql`
- Create en `api/src/main/java/io/cradesk/api/waitlist/`: `AccessRequest.java`, `AccessRequestRepository.java`, `AccessRequestService.java`, `AccessRequestController.java`, `AccessRequestForm.java`, `AccessRequestResponse.java`
- Test: `api/src/test/java/io/cradesk/api/waitlist/AccessRequestApiIT.java`

**Interfaces:**
- Consumes: `Emails.normalize`, `Constraints.isViolated` y la autoridad `ROLE_PLATFORM_ADMIN` (Tarea 5).
- Produces:
  - Tabla `access_requests`: `id uuid pk`, `email varchar(254) not null` con único `access_requests_email_unique ON (lower(email))`, `company varchar(120)`, `message varchar(1000)`, `created_at timestamptz not null`.
  - `POST /api/access-requests` con `AccessRequestForm(@NotBlank @Email @Size(max = 254) String email, @Size(max = 120) String company, @Size(max = 1000) String message)`. Con datos válidos → **202 sin cuerpo**. Un duplicado no se guarda otra vez y también da 202: si choca con el índice, `Constraints.isViolated` lo trata como duplicado y no responde 409.
  - `GET /api/admin/access-requests` → 200 `List<AccessRequestResponse(String email, String company, String message, Instant createdAt)>`, de la más reciente a la más antigua. Requiere `X-Admin-Token`.

- [ ] **Step 1 (Claude):** Escribir `AccessRequestApiIT` con estos tests:
  - `validRequestIsAccepted`: 202, sin sesión y con `csrf()`.
  - `duplicateEmailIgnoringCaseIsAcceptedButStoredOnce`: `"Alice@Example.com"` y luego `" alice@example.com "` → 202 las dos veces y 1 fila.
  - `invalidEmailReturns400`: `"not-an-email"` → 400 con `errors.email`.
  - `oversizedMessageReturns400`: 1001 caracteres → 400 con `errors.message`.
  - `adminCanListRequests` y `listRequiresAdminToken` (sin token → 401).
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Revisión, `docs/aprendizaje.md` y commit con el mensaje `feat(waitlist): public request-access form (V6)`.

### Task 9 [Claude]: Endurecer el catálogo (`V7`)

Cierra los hallazgos menores restantes de la revisión de las etapas 0 y 1.

**Files:**
- Create: `api/src/main/resources/db/migration/V7__product_row_version.sql` (`ALTER TABLE products ADD COLUMN row_version bigint NOT NULL DEFAULT 0`)
- Create: `api/src/main/java/io/cradesk/api/common/Names.java`
- Modify: `Product.java`, `ProductRequest.java`, `ProductResponse.java`, `ProductService.java`, `ProductRepository.java`, `ApiExceptionHandler.java`, `InvitationService.java`
- Test: `ProductApiIT`, `CatalogConstraintsIT`, `InvitationApiIT`

**Interfaces:**
- Produces:
  - `Product` con `@Version @Column(name = "row_version") long rowVersion`.
  - `ProductResponse` agrega `long rowVersion`.
  - `ProductRequest` agrega `Long rowVersion`, opcional. Si viene en un `PUT` y no coincide → 409 `"Product was modified by someone else"`. Si `ObjectOptimisticLockingFailureException` llega al handler → también 409.
  - Normalización de nombres: `strip()` y colapsar los espacios internos (`"Smart   Lock"` → `"Smart Lock"`). Una `description` en blanco se guarda como `null`. Va en un método `static String normalizeName(String)` de `common/Names.java`; en esta tarea también se aplica a `organizationName` al aceptar una invitación (Tarea 5), con su test en `InvitationApiIT`.
  - Carrera entre borrar y agregar una versión: `delete` y `addVersion` bloquean la fila del producto con `@Lock(PESSIMISTIC_WRITE)` mediante `Optional<Product> findLockedByIdAndOrganizationId(UUID, UUID)`.

- [ ] **Step 1:** Escribir los tests:
  - `ProductApiIT.staleRowVersionReturns409`: `PUT` con `rowVersion` 0, otra vez con `rowVersion` 0 → el segundo da 409.
  - `ProductApiIT.namesCollapseInnerAndUnicodeSpaces`: `"Smart   Lock"` se guarda como `"Smart Lock"` y luego `"smart lock"` → 409.
  - `ProductApiIT.blankDescriptionIsStoredAsNull`
  - `CatalogConstraintsIT.deleteWaitsForConcurrentVersionInsert`: dos hilos con `TransactionTemplate` y un `CountDownLatch`. Uno agrega una versión y espera antes de confirmar; el otro borra el producto. El resultado debe ser `ProductHasVersionsException` (409), no un 500.
- [ ] **Step 2:** Ejecutar `./mvnw clean verify`. Resultado esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Ejecutar `./mvnw clean verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Commit con el mensaje `fix(catalog): optimistic locking, name normalization and delete/version race (V7)`.

### Task 10 [Claude]: Cierre de la Etapa 2

**Files:**
- Modify: `README.md` (estado, tabla de la API con autenticación, variables `CRADESK_ADMIN_TOKEN`, `CRADESK_DEMO_ENABLED`, `SESSION_COOKIE_SECURE`), `scripts/demo.sh`, `.env.example`

- [ ] **Step 1:** `demo.sh` reescrito con sesión: pedir el token CSRF y entrar como demo; mostrar los productos de ejemplo y que un `POST` da 403. Después crear una invitación con el token de admin, aceptarla, crear un producto, generar una clave de API, usarla con `curl` y revocarla para ver el 401.
- [ ] **Step 2:** Ejecutar la API según el README y luego `bash scripts/demo.sh`. Resultado esperado: cada paso con el código HTTP indicado en su título.
- [ ] **Step 3:** Revisión de toda la rama con un revisor independiente (superpowers:requesting-code-review) y corrección de lo crítico e importante.
- [ ] **Step 4:** Commit con el mensaje `docs: stage 2 README, demo script and env`.
- [ ] **Step 5:** Preguntar al usuario si quiere crear ya el repo público y hacer push. Es una acción pública, así que va solo con su confirmación.

---

**Fuera de alcance de esta etapa (anotado para no olvidarlo):**
- Límite de peticiones (rate limit) en `/api/auth/login` por IP y en `/api/access-requests` contra spam: Etapa 8, junto con el despliegue.
- Sesiones persistentes en BD (Spring Session JDBC): Etapa 8, solo si los despliegues molestan.
- Tope diario de llamadas a la IA por cuenta invitada: Etapa 6, con el módulo `reporting`.
- Verificación de email, recuperación de contraseña y registro abierto: enero (spec §2).
