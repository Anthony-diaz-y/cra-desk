# CRA Desk — Etapas 0 y 1: Base del proyecto y Catálogo — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Modo tutor (decisión del usuario):** las tareas marcadas **[Claude]** las implementa Claude y luego las explica línea por línea. En las tareas marcadas **[Alumno]**:
> 1. Claude da una lección corta del concepto Java/Spring que hace falta.
> 2. Claude escribe el test (el test es la especificación).
> 3. El usuario escribe el código.
> 4. Claude revisa y explica cada corrección.
> 5. El usuario anota 3 líneas en `docs/aprendizaje.md`.
>
> Claude no escribe el código de producción de una tarea [Alumno]; si el alumno se atasca, da pistas cada vez más concretas.

**Goal:** Monorepo funcionando con PostgreSQL local y una API Spring Boot que gestiona productos y sus versiones (módulo `catalog`), con tests de integración.

**Architecture:** Monorepo `cra-desk/` con `api/` (Spring Boot, monolito modular por paquetes). PostgreSQL en Docker Compose para desarrollo y en Testcontainers para tests. Esquema gestionado con Flyway.

**Tech Stack:** Java 21.0.10, Maven 3.9.16, Spring Boot (la 4.x estable que sirva start.spring.io), Spring Web, Spring Data JPA, Bean Validation, Flyway, PostgreSQL 17, Testcontainers, JUnit 5, AssertJ. Git 2.52 y gh 2.103 (ya instalados, verificado 2026-11-13).

**Spec:** `docs/superpowers/specs/2026-11-13-cra-desk-design.md`

## Global Constraints

- Monorepo en `C:\workSpace\cra-desk` con carpetas `api/`, `cli/`, `web/`, `docs/`, `.github/workflows/`.
- Código, identificadores, mensajes de la API y README en inglés. Explicaciones de tutoría y `docs/aprendizaje.md` en español.
- Licencia: Business Source License 1.1 (Change License: Apache 2.0, Change Date: 4 años desde la publicación).
- Nunca subir secretos: credenciales solo en variables de entorno; `.env` en `.gitignore`.
- Paquete raíz Java: `io.cradesk.api`. Cada módulo es un paquete (`io.cradesk.api.catalog`, …), no un servicio separado.
- Sin microservicios, Kubernetes ni colas.
- Las respuestas de error usan `ProblemDetail` (RFC 9457) de Spring.
- Antes de cualquier acción pública (crear el repo en GitHub, hacer push) se pide confirmación al usuario.

## Review Focus

1. **Nombre de producto duplicado**, también por carrera entre dos peticiones: se espera 409, no 500. Lo cubre la restricción `UNIQUE` en la BD y su conversión a 409 (Tarea 4).
2. **Nombre en blanco, solo espacios o de más de 120 caracteres:** se espera 400 con detalle por campo (Tarea 4).
3. **Producto o versión inexistente** (`GET`/`PUT`/`DELETE` por id desconocido, o versión de un producto que no existe): se espera 404 (Tareas 4 y 5).
4. **Borrar un producto que ya tiene versiones:** se espera 409. No se borra en cascada, porque son registros de cumplimiento (Tarea 5).
5. **Versión duplicada en el mismo producto, o versión con espacios alrededor:** se recorta y luego da 409 si ya existe. La misma versión en otro producto sí está permitida (Tarea 5).

---

### Task 1 [Claude]: Monorepo, licencia y PostgreSQL local

**Files:**
- Create: `README.md`, `LICENSE`, `.gitignore`, `.gitattributes`, `.env.example`, `docker-compose.yml`, `docs/aprendizaje.md`, `api/.gitkeep`, `cli/.gitkeep`, `web/.gitkeep`

**Interfaces:**
- Produces: base `cradesk` en `localhost:5432`, usuario `cradesk`. La contraseña viene de `.env` (`POSTGRES_PASSWORD`); `.env.example` la documenta sin valor real.

- [ ] **Step 1:** `git init -b main` en `C:\workSpace\cra-desk` (ya contiene `docs/superpowers/`).
- [ ] **Step 2:** Crear los archivos:
  - `docker-compose.yml`: servicio `postgres` con imagen `postgres:17`, volumen con nombre y healthcheck `pg_isready`.
  - `.gitignore`: Java/Maven, Node, Go, IDE y `.env`.
  - `.gitattributes`: `* text=auto eol=lf`.
  - `LICENSE`: BSL 1.1 con Licensor "the CRA Desk author", Additional Use Grant "non-production and non-commercial use", Change License Apache 2.0.
- [ ] **Step 3:** Ejecutar `docker compose up -d` y luego `docker compose exec postgres pg_isready -U cradesk`. Resultado esperado: `accepting connections`.
- [ ] **Step 4:** Commit con el mensaje `chore: monorepo skeleton, BSL license, local postgres`.
- [ ] **Step 5:** Explicar al alumno qué hace cada archivo y por qué (5–10 min).

### Task 2 [Claude]: Esqueleto de la API Spring Boot con test de humo

**Files:**
- Create: `api/` generado con start.spring.io (Maven, Java 21, groupId `io.cradesk`, artifactId `api`, paquete `io.cradesk.api`; dependencias `web`, `data-jpa`, `validation`, `flyway`, `postgresql`, `actuator`, `testcontainers`).
- Create: `api/src/main/resources/application.yml`. Toma la URL, el usuario y la contraseña de la BD de variables de entorno, con valores por defecto para local. Define `spring.jpa.hibernate.ddl-auto=validate` y `spring.jpa.open-in-view=false`.
- Create: `api/src/test/java/io/cradesk/api/support/IntegrationTest.java`, una anotación o clase base con `@SpringBootTest` y un `PostgreSQLContainer("postgres:17")` compartido mediante `@ServiceConnection`.
- Test: `api/src/test/java/io/cradesk/api/HealthIT.java`

**Interfaces:**
- Produces: clase base `IntegrationTest` (las Tareas 3–5 la extienden) y la aplicación `io.cradesk.api.ApiApplication`.

- [ ] **Step 1:** Escribir el test `HealthIT.actuatorHealthIsUp`, que hace `GET /actuator/health` y espera status 200 con body `"status":"UP"`.
- [ ] **Step 2:** Ejecutar `cd api && ./mvnw verify`. Resultado esperado: PASS. Requiere Docker en marcha para Testcontainers.
- [ ] **Step 3:** Ejecutar `./mvnw spring-boot:run` contra el Postgres de compose y luego `curl localhost:8080/actuator/health`. Resultado esperado: `{"status":"UP"}`.
- [ ] **Step 4:** Commit con el mensaje `chore(api): spring boot skeleton with testcontainers smoke test`.
- [ ] **Step 5:** Lección 0. Recorrer con el alumno:
  - la estructura de Maven (`pom.xml`, `src/main`, `src/test`);
  - qué es Spring Boot (inyección de dependencias, autoconfiguración);
  - cómo se lee un test.

### Task 3 [Alumno]: Entidad `Product`, migración y repositorio

**Lección previa (Claude):** clases, constructores, encapsulación, `enum`, anotaciones; qué es JPA/Hibernate y qué hace Flyway.

**Files:**
- Create: `api/src/main/resources/db/migration/V1__catalog.sql`
- Create: `api/src/main/java/io/cradesk/api/catalog/Product.java`
- Create: `api/src/main/java/io/cradesk/api/catalog/ProductClass.java`
- Create: `api/src/main/java/io/cradesk/api/catalog/ProductRepository.java`
- Test: `api/src/test/java/io/cradesk/api/catalog/ProductRepositoryIT.java`

**Interfaces:**
- Produces:
  - `enum ProductClass { DEFAULT, IMPORTANT_CLASS_I, IMPORTANT_CLASS_II, CRITICAL }`, que son las categorías del CRA.
  - Entidad `Product` con:
    - `UUID id`, generado;
    - `String name` (≤120, único);
    - `String description` (nullable, ≤2000);
    - `ProductClass productClass`;
    - `Instant createdAt` y `Instant updatedAt`.
  - `interface ProductRepository extends JpaRepository<Product, UUID>` con `boolean existsByNameIgnoreCase(String name)`.
  - Tablas en `V1__catalog.sql`:
    - `products`: `id uuid pk`, `name varchar(120) not null`, índice único sobre `lower(name)`, `description varchar(2000)`, `product_class varchar(32) not null`, `created_at timestamptz not null`, `updated_at timestamptz not null`.
    - `product_versions`: `id uuid pk`, `product_id uuid not null references products(id)`, `version varchar(64) not null`, `released_at date`, `created_at timestamptz not null`, `unique(product_id, version)`.

- [ ] **Step 1 (Claude):** Escribir `ProductRepositoryIT` con dos tests:
  - `savesAndFindsProduct`: guarda un producto y lo recupera por id con los mismos campos.
  - `existsByNameIgnoresCase`: guarda "Smart Lock" y comprueba que `existsByNameIgnoreCase("smart lock")` devuelve `true`.
- [ ] **Step 2:** Ejecutar `./mvnw verify -Dit.test=ProductRepositoryIT`. Resultado esperado: FAIL por error de compilación (aún no existe `Product`).
- [ ] **Step 3 (Alumno):** Escribir la migración, el enum, la entidad y el repositorio.
- [ ] **Step 4:** Ejecutar `./mvnw verify`. Resultado esperado: PASS en todo. Fíjate en que `ddl-auto=validate` comprueba que la entidad y la migración coinciden.
- [ ] **Step 5:** Revisión de Claude, anotación en `docs/aprendizaje.md` y commit con el mensaje `feat(catalog): product entity and V1 migration`.

### Task 4 [Alumno]: API REST de productos con validación y errores

**Lección previa (Claude):**
- `record` y DTOs;
- capas controller → service → repository;
- `@Transactional`;
- Bean Validation;
- excepciones y `@RestControllerAdvice`;
- `ProblemDetail`.

**Files:**
- Create en `api/src/main/java/io/cradesk/api/catalog/`: `ProductController.java`, `ProductService.java`, `ProductRequest.java`, `ProductResponse.java`, `ProductNotFoundException.java`, `DuplicateProductException.java`
- Create: `api/src/main/java/io/cradesk/api/common/ApiExceptionHandler.java`
- Test: `api/src/test/java/io/cradesk/api/catalog/ProductApiIT.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository` y `ProductClass` (Tarea 3).
- Produces:
  - `record ProductRequest(@NotBlank @Size(max=120) String name, @Size(max=2000) String description, @NotNull ProductClass productClass)`.
  - `record ProductResponse(UUID id, String name, String description, ProductClass productClass, Instant createdAt, Instant updatedAt)`.
  - `ProductService` con:
    - `ProductResponse create(ProductRequest)`
    - `List<ProductResponse> list()`, ordenada por nombre
    - `ProductResponse get(UUID)`
    - `ProductResponse update(UUID, ProductRequest)`
    - `void delete(UUID)`
  - Endpoints:
    - `POST /api/products` → 201 con cabecera `Location`
    - `GET /api/products` → 200
    - `GET /api/products/{id}` → 200
    - `PUT /api/products/{id}` → 200
    - `DELETE /api/products/{id}` → 204
  - Errores, todos como `ProblemDetail`:
    - validación → 400, con propiedad `errors` (mapa campo → mensaje);
    - no encontrado → 404;
    - duplicado → 409. También cuando la BD lanza `DataIntegrityViolationException` por el índice único.
  - El servicio recorta los espacios de `name` antes de validar el duplicado y antes de guardar.

- [ ] **Step 1 (Claude):** Escribir `ProductApiIT` (MockMvc o `RestClient` contra el puerto aleatorio) con estos tests:
  - `createReturns201WithLocation`
  - `listIsSortedByName`
  - `getUnknownIdReturns404`
  - `blankNameReturns400WithFieldError`: con `"   "`, `errors.name` existe.
  - `nameLongerThan120Returns400`
  - `duplicateNameDifferentCaseReturns409`: crea "Smart Lock" y luego "smart lock ".
  - `updateChangesFieldsAndUpdatedAt`
  - `deleteReturns204ThenGetReturns404`
- [ ] **Step 2:** Ejecutar `./mvnw verify`. Resultado esperado: FAIL, porque los endpoints no existen.
- [ ] **Step 3 (Alumno):** Implementar los DTOs, el servicio, el controller, las excepciones y el `ApiExceptionHandler`.
- [ ] **Step 4:** Ejecutar `./mvnw verify`. Resultado esperado: PASS en todo.
- [ ] **Step 5:** Prueba manual con `curl` contra `spring-boot:run`, revisión de Claude, anotación en `docs/aprendizaje.md` y commit con el mensaje `feat(catalog): products REST API with validation and problem details`.

### Task 5 [Alumno]: Versiones de producto y borrado protegido

**Lección previa (Claude):** relaciones JPA (`@ManyToOne`, por qué evitar `@OneToMany` con cascade aquí), `Optional`, streams y `LocalDate`.

**Files:**
- Create en `api/src/main/java/io/cradesk/api/catalog/`: `ProductVersion.java`, `ProductVersionRepository.java`, `VersionRequest.java`, `VersionResponse.java`, `DuplicateVersionException.java`, `ProductHasVersionsException.java`
- Modify: `ProductService.java` (añadir los métodos de versiones y el borrado protegido), `ProductController.java`, `ApiExceptionHandler.java`
- Test: `api/src/test/java/io/cradesk/api/catalog/ProductVersionApiIT.java`

**Interfaces:**
- Consumes: `ProductService`, `ProductNotFoundException` y el `ApiExceptionHandler` de la Tarea 4.
- Produces:
  - Entidad `ProductVersion` con `UUID id`, `Product product` (`@ManyToOne(fetch = LAZY)`), `String version`, `LocalDate releasedAt` y `Instant createdAt`.
  - `ProductVersionRepository` con:
    - `List<ProductVersion> findByProductIdOrderByCreatedAtDesc(UUID)`
    - `boolean existsByProductIdAndVersion(UUID, String)`
    - `boolean existsByProductId(UUID)`
  - `record VersionRequest(@NotBlank @Size(max=64) String version, LocalDate releasedAt)` y `record VersionResponse(UUID id, UUID productId, String version, LocalDate releasedAt, Instant createdAt)`.
  - Endpoints:
    - `POST /api/products/{productId}/versions` → 201
    - `GET /api/products/{productId}/versions` → 200, de la más reciente a la más antigua
  - `DELETE /api/products/{id}` → 409 si el producto tiene versiones.

- [ ] **Step 1 (Claude):** Escribir `ProductVersionApiIT` con estos tests:
  - `createVersionReturns201`
  - `versionIsTrimmed`: `" 1.2.0 "` se guarda como `"1.2.0"`.
  - `duplicateVersionSameProductReturns409`
  - `sameVersionOnOtherProductIsAllowed`
  - `versionForUnknownProductReturns404`
  - `listReturnsNewestFirst`
  - `deleteProductWithVersionsReturns409`
- [ ] **Step 2:** Ejecutar `./mvnw verify`. Resultado esperado: FAIL.
- [ ] **Step 3 (Alumno):** Implementar.
- [ ] **Step 4:** Ejecutar `./mvnw verify`. Resultado esperado: PASS en todo, incluidas las Tareas 2–4.
- [ ] **Step 5:** Revisión de Claude, anotación en `docs/aprendizaje.md` y commit con el mensaje `feat(catalog): product versions and protected delete`.
- [ ] **Step 6:** Cierre de la Etapa 1:
  - demo en `curl` del flujo completo (crear producto, añadir dos versiones, listar, intentar borrar);
  - preguntar al usuario si quiere crear ya el repo público en GitHub (`gh repo create cra-desk --public`) y hacer push. Es una acción pública, así que va solo con su confirmación.

---

**Siguientes planes (uno por etapa, se escriben al terminar esta):**
- Etapa 2: `identity` (sesión, invitaciones, demo de solo lectura, claves de API, `organization_id` en el catálogo con migración V2).
- Etapa 3: `sbom`.
- Etapa 4: `vulns`.
- Etapa 5: CLI en Go.
- Etapa 6: `reporting` y `audit`.
- Etapa 7: `web`.
- Etapa 8: DevOps.
- Etapa 9: pulido.
