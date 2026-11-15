# Handoff para Claude Code (copiar y pegar como primer mensaje)

---

Continúa mi proyecto **CRA Desk** en `C:\workSpace\cra-desk` (Windows 11). Abre Claude Code en esa carpeta. Háblame en español; el código, los commits y la API van en inglés.

## Qué es y por qué

CRA Desk es un SaaS de cumplimiento del **EU Cyber Resilience Act** para fabricantes pequeños de IoT/firmware/software. Incluye:
- carga de SBOM por versión de producto;
- monitoreo de vulnerabilidades (OSV + CISA KEV);
- eventos reportables que una persona confirma, con relojes de 24 h, 72 h y 14 días;
- borradores de reportes hechos con IA y aprobados por una persona;
- auditoría con cadena de hashes.

Mis objetivos:
1. **Portafolio** para empresas y clientes (full stack: desarrollo, seguridad, despliegue, mantenimiento).
2. **Aprender Java y Go** construyéndolo. Sé poco Java y nada de Go.
3. Desplegarlo hacia fin de 2026 como demo con lista de espera.

## Lee primero (fuente de verdad)

1. `docs/superpowers/specs/2026-11-13-cra-desk-design.md`: el diseño aprobado, con decisiones, etapas 0 a 9, no-metas y anti-metas.
2. `docs/superpowers/plans/2026-11-13-etapa-0-1-base-y-catalogo.md`: el plan de las etapas 0 y 1, ya completado.
3. `README.md`: el estado, la tabla de la API y cómo correr la demo.
4. `git log --oneline` y el ledger `.superpowers/sdd/2026-11-13-etapa-0-1-base-y-catalogo/progress.md`. El ledger está en `.gitignore`; si no existe, guíate por `git log`.

## Estado (2026-11-15)

- ✅ **Etapas 0 y 1 completas** (8 commits locales, rama `main`, sin push).
  - Tiene: monorepo, PostgreSQL 17 en Docker, API Spring Boot 4.1.2 (Java 21), módulo `catalog` (productos y versiones) y errores RFC 9457.
  - **19 tests de integración en verde** (`cd api && ./mvnw verify`).
  - Demo: `bash scripts/demo.sh`, con la API corriendo.
- Quién escribió qué: la Tarea 3 (entidad `Product`, migración `V1`) la escribí yo. Las tareas 4 y 5 las terminó Claude porque me faltó tiempo.
- **Pendiente 1:** la revisión completa de la rama de las etapas 0 y 1 (con el skill de code review) no se hizo. Hazla antes de seguir.
- **Pendiente 2:** escribir el plan de la **Etapa 2: `identity` y seguridad**. Según el diseño:
  - sesión con cookie HttpOnly;
  - cuentas por invitación;
  - cuenta demo de solo lectura;
  - claves de API con hash para la CLI;
  - lista de espera "Request access";
  - `organization_id` en el catálogo con la migración `V2`;
  - tests de aislamiento entre empresas.

## Decisiones ya tomadas (no las reabras)

- **Stack:** API Spring Boot (Java 21) + PostgreSQL + Flyway; CLI en Go; web en Next.js + TypeScript; Docker; GitHub Actions; Railway; Claude API (Haiku) para los borradores.
- **Arquitectura:** monolito modular. Un paquete por módulo bajo `io.cradesk.api`. Nada de microservicios ni Kubernetes.
- **Relojes:** arrancan cuando una persona confirma el hallazgo y se detienen al marcar el reporte "enviado a ENISA", con hora y referencia opcional.
- **Diciembre:** demo de solo lectura con lista de espera; cuentas reales por invitación. La demo no llama a la IA.
- **Repositorio:** público con **Business Source License 1.1**. Crear el repo o hacer push solo **con mi confirmación**.
- **Maven wrapper fijado en 3.9.16.** No lo subas a 3.10: mi IntelliJ 2025.2 no lo soporta.

## Cómo trabajar conmigo

- Al empezar, **pregúntame en qué modo quiero trabajar hoy**:
  - **Tutor** (preferido cuando tengo tiempo): me das una lección corta con un ejemplo de otro dominio (`Book`/`BookRepository`), escribes el test, yo escribo el código, revisas explicando cada error, hacemos el commit y yo anoto 3 líneas en `docs/aprendizaje.md`. No me des la solución completa; dame pistas progresivas. La infraestructura (Docker, CI, configuración) sí la haces tú, pero me la explicas.
  - **Avance directo** (cuando estoy apurado): implementas tú con TDD y al final me explicas qué hiciste y qué archivos debo leer para poder explicarlo.
- Explicaciones simples, con tablas y ejemplos cortos.
- Errores que ya cometí: código Java dentro de un `.sql`; olvidar comas y `;` en SQL; poner en un método de repositorio el nombre de un campo que no existe.

## Notas de entorno

- **Docker Desktop debe estar encendido** para los tests (Testcontainers) y para la base local. Si no lo está, inícialo: `"C:\Program Files\Docker\Docker\Docker Desktop.exe"`.
- La contraseña local de la BD está en `.env`, que no se versiona. Para correr la API: `set -a && . ../.env && set +a && ./mvnw spring-boot:run` desde `api/`.
- Los tests de integración se llaman `*IT.java` y los corre `maven-failsafe-plugin` en `./mvnw verify`.
- La anotación `@IntegrationTest` (en `api/src/test/java/io/cradesk/api/support/`) arranca la app con PostgreSQL de Testcontainers y MockMvc.
- Todos los errores pasan por `ApiExceptionHandler`, que extiende `ResponseEntityExceptionHandler`. La validación devuelve una propiedad `errors` (campo → mensaje). Los mensajes de la API están forzados a inglés (`spring.web.locale: en`).

**Primer paso:** lee los archivos de arriba, resúmeme en 3 líneas dónde estamos, pregúntame el modo de trabajo y propón empezar por la revisión pendiente de las etapas 0 y 1.
