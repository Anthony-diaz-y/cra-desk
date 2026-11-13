# CRA Desk — Documento de diseño

Fecha: 2026-11-13 · Estado: aprobado por el usuario (2026-11-13)

## 1. Intención y porqué

CRA Desk es un SaaS que ayuda a fabricantes pequeños de productos con software (IoT, firmware, software instalable) que venden en la Unión Europea a cumplir el **Cyber Resilience Act (CRA)**:

- Desde el **11-sep-2026** deben reportar a ENISA las vulnerabilidades explotadas activamente: alerta temprana en **24 h**, notificación en **72 h**, informe final en **14 días**.
- Desde el **11-dic-2027** deben tener SBOM, gestión de vulnerabilidades y expediente técnico (Anexo I y VII).
- Hoy solo hay herramientas baratas pensadas para SaaS (~$99/mes) o servicios de €2–5k/mes. No hay nada intermedio para fabricantes pequeños.

Base de la idea: `C:\workSpace\reports\Idea de software innovador 2026.md`.

**Por qué lo construye el usuario (orden de prioridad):**

1. **Portafolio** para empresas y clientes. Debe demostrar:
   - detectar una necesidad real y vigente y diseñar la solución;
   - dominio full stack: backend, frontend, seguridad, despliegue y mantenimiento;
   - elegir la tecnología adecuada para cada pieza y saber justificarla.
2. **Aprender Java y Go** construyéndolo. Hoy sabe poco Java y casi nada de Go. Claude actúa de tutor.
3. **Secundario, hacia fin de 2026:** dejarlo en línea, ver si alguien lo compra y empezar a publicitarlo.

## 2. Metas, no-metas y anti-metas

**Metas (versión de diciembre 2026):**

- Un usuario se registra y crea su empresa, sus productos y las versiones de cada producto.
- Sube el SBOM (CycloneDX JSON) de una versión desde la web o con la CLI en Go (también usable como GitHub Action).
- El sistema cruza los componentes del SBOM con vulnerabilidades conocidas (OSV) y marca las que están en el catálogo KEV de CISA (explotadas activamente).
- Una vulnerabilidad del catálogo KEV crea un **hallazgo para revisar** (no abre el evento por sí sola). Una persona lo revisa y confirma que su producto está afectado y la vulnerabilidad explotada. Esa confirmación abre el **evento reportable** y arranca los tres relojes (24 h / 72 h / 14 d), que se ven en el panel. Si descarta el hallazgo, debe escribir un motivo, que queda en la auditoría. *(Decisión del usuario.)*
- Cada reloj se detiene cuando el usuario marca ese reporte como **"enviado a ENISA"**, con la hora del envío y un número de referencia opcional. Aprobar o exportar el borrador no detiene el reloj. Estados de cada plazo: `pendiente`, `por vencer` (menos del 25% del tiempo restante), `vencido` o `enviado` (a tiempo o tarde). Un plazo vencido no bloquea nada, solo se marca en rojo. *(Decisión de Claude.)*
- La IA (Claude API) redacta los borradores de cada reporte. Una persona los revisa, edita y aprueba, y luego los exporta (PDF/JSON).
- **Registro de auditoría** inmutable de cada acción (cadena de hashes).
- Desplegado en Railway con integración y despliegue continuos, escaneo de seguridad y monitoreo.
- **Acceso en diciembre: demo pública y lista de espera.** *(Decisión del usuario.)*
  - Una cuenta demo de solo lectura con datos de ejemplo y un botón "Probar demo".
  - Las cuentas reales se crean por invitación (código o comando de admin).
  - Un formulario "Request access" guarda emails en la base de datos.
  - En la demo, la IA usa borradores pregenerados en caché y no llama a Claude, así no hay gasto. Las cuentas invitadas tienen un tope diario de llamadas a la IA.
  - El registro abierto (con verificación de email, recuperación de contraseña y páginas legales) queda para enero.
- **Repositorio público en GitHub con Business Source License 1.1** (uso no comercial libre; nadie puede ofrecerlo como servicio sin permiso; pasa a Apache 2.0 a los 4 años). *(Decisión del usuario.)* La CLI y la GitHub Action van en el mismo monorepo y se publican con GitHub Releases (`cli/action.yml`). Nunca se suben secretos ni datos reales; se usa gitleaks en CI.
- **Dogfooding:** CRA Desk sube su propio SBOM a sí mismo en cada despliegue.
- Datos de demo y un README de portafolio (arquitectura, decisiones y por qué).

**No-metas (quedan para después de diciembre):**

- Enviar reportes directamente a ENISA. Su plataforma única de reporte no tiene una API pública confirmada, así que solo se exporta.
- Generador completo del expediente técnico del Anexo I (en diciembre solo un esqueleto en Markdown).
- SPDX, VEX, invitaciones de equipo con varios roles, facturación con Stripe, notificaciones por email/Slack.
- Generar SBOM (de eso se encargan herramientas existentes como Syft; CRA Desk los recibe).

**Anti-metas (si pasa esto, fracasó aunque funcione):**

- Código que el usuario no entiende o no puede explicar en una entrevista.
- Sobreingeniería: microservicios, Kubernetes, colas de mensajes sin necesidad.
- Presentarse como asesoría legal. La interfaz y el README dicen que es una herramienta de apoyo.
- Seguridad débil en una herramienta de seguridad: secretos en el repo, sin control de acceso por empresa, dependencias vulnerables sin revisar.

## 3. Restricciones

- **Tiempo:** unas 3 h por día entre semana y 4 h los fines de semana, ~23 h/semana, ~160 h hasta el 31-dic-2026, incluido el aprendizaje.
- **Experiencia:** poco Java, casi nada de Go, algo de Node/TypeScript, Python y Kotlin.
- **Presupuesto:** casi cero. Railway (el usuario ya lo usa), APIs gratuitas (OSV, KEV de CISA) y Claude API con bajo consumo.
- **Entorno:** Windows 11. Java 21 y Go se instalarán al empezar.
- **Idioma:** producto y README en inglés (mercado global); explicaciones de tutoría en español.

## 4. Enfoques considerados

| Enfoque | A favor | En contra | Decisión |
|---|---|---|---|
| **A. Cada pieza con su herramienta:** API en Spring Boot (Java 21), CLI en Go, frontend Next.js | Cada elección se puede justificar: Java para lógica empresarial y regulada, Go para un binario único en CI, Next.js para la interfaz. Practica Java y Go. | Tres tecnologías que mantener | **Elegido por el usuario** |
| B. Todo en TypeScript (Next.js de punta a punta) | Lo más rápido | Muestra poco backend serio; no practica Java ni Go | Descartado |
| C. Microservicios en Go con Kubernetes | Suena impresionante | Sobreingeniería para una persona; contradice "elegir lo que encaja" | Descartado |

## 5. Lo decidido sobre el cómo

**Arquitectura: monolito modular más dos clientes.**

```
cra-desk/                      (monorepo)
├── api/        Spring Boot 3, Java 21, Maven, PostgreSQL, Flyway
├── cli/        Go: binario `cradesk` + GitHub Action
├── web/        Next.js (App Router) + TypeScript + Tailwind
├── docs/       diseño, decisiones (ADR), guía de despliegue
└── .github/workflows/   CI/CD
```

**Módulos de la API (paquetes de un solo proyecto, no servicios separados):**

- `identity`: registro, login, empresas (multi-tenant por `organization_id`), claves de API para la CLI.
- `catalog`: productos y versiones.
- `sbom`: recibe y valida CycloneDX JSON y guarda los componentes (purl, nombre, versión).
- `vulns`:
  - consulta OSV por lotes de purl;
  - descarga el catálogo KEV cada día;
  - re-escanea todas las versiones periódicamente con tareas programadas de Spring;
  - crea hallazgos (*findings*).
- `reporting`:
  - eventos reportables, plazos de 24 h, 72 h y 14 d;
  - borradores con Claude API y aprobación humana;
  - exportación.
- `audit`: registro de auditoría append-only con cadena de hashes (cada entrada guarda el hash de la anterior), con un endpoint para verificar la integridad.

**Seguridad (parte del portafolio):**

- Spring Security con sesión en el navegador (cookie HttpOnly) y claves de API con hash para la CLI.
- Aislamiento por empresa en cada consulta, con tests que lo comprueban.
- Validación de entrada y límite de tamaño de SBOM.
- Secretos solo en variables de entorno.
- En CI: CodeQL, escaneo de dependencias y del contenedor (Trivy) y el propio SBOM subido a CRA Desk.

**Calidad:**

- Tests unitarios y de integración con Testcontainers (PostgreSQL real).
- Tests de la CLI en Go.
- Pocos tests en el frontend.

**Despliegue:**

- Dockerfile por app.
- Railway: API, web y PostgreSQL.
- GitHub Actions: tests, escaneo y despliegue en `main`.
- Monitoreo con Spring Actuator, logs estructurados y Sentry (plan gratuito) para errores.

**Modo tutor (cómo se trabaja):**

| Lo escribe el usuario (Claude explica, pone la tarea y revisa) | Lo arma Claude y lo explica línea por línea |
|---|---|
| Entidades y servicios de dominio | Esqueletos de proyecto |
| Lógica de plazos | Dockerfiles |
| Cruce SBOM ↔ vulnerabilidades | Workflows de CI |
| Cadena de auditoría | Configuración de Railway |
| La CLI en Go | |
| Pantallas principales | |

Cada etapa sigue este ciclo:

1. Concepto breve con un ejemplo.
2. Tarea concreta.
3. Revisión con correcciones explicadas.
4. Un "qué aprendiste" de 3 líneas en `docs/aprendizaje.md`, que sirve para entrevistas.

Las etapas terminan con algo demostrable y con un commit.

## 6. Etapas (orden y estimación)

| # | Etapa | Aprende | Horas aprox. |
|---|---|---|---|
| 0 | Instalación (Java 21, Go, Docker), monorepo, PostgreSQL local | Herramientas | 6 |
| 1 | Java básico aplicado y Spring Boot: `catalog` (CRUD productos/versiones), Flyway, tests | Java, Spring, JPA, tests | 25 |
| 2 | `identity` y seguridad: invitaciones, login, cuenta demo de solo lectura, multi-tenant, claves de API, lista de espera | Spring Security | 18 |
| 3 | `sbom`: subir y parsear CycloneDX, guardar componentes | JSON, validación, Testcontainers | 12 |
| 4 | `vulns`: OSV, KEV, tareas programadas, hallazgos | Clientes HTTP, scheduling | 18 |
| 5 | CLI en Go: `cradesk login/upload`, GitHub Action | Go desde cero, binarios multiplataforma | 18 |
| 6 | `reporting` y `audit`: revisión de hallazgos, relojes, borradores con Claude, aprobación, "enviado a ENISA", exportación, cadena de hashes | Máquinas de estado, IA aplicada, criptografía básica | 26 |
| 7 | Frontend Next.js: login, productos, hallazgos, panel de plazos, editor de reportes | Next.js, consumo de API | 22 |
| 8 | DevOps: Docker, CI/CD, escaneos, Railway, monitoreo, dogfooding | DevOps y seguridad de la cadena de suministro | 12 |
| 9 | Pulido de portafolio: datos de demo, README con arquitectura y ADR, video corto | Comunicación técnica | 5 |
| | **Total** | | **~162** |

Si el tiempo no alcanza, se recorta en este orden: exportación PDF (queda solo JSON), Sentry y el video. Nunca se recortan seguridad ni tests.

## 7. Lo que queda a criterio de quien implemente

- Esquema exacto de tablas, nombres de endpoints y librerías menores (cliente HTTP, parser JSON, componentes de UI).
- Cada cuánto se re-escanea (propuesta: diario y al subir un SBOM).
- Diseño visual del frontend (sobrio, estilo herramienta de seguridad).
- Formato del borrador de los reportes, siguiendo los campos que el CRA exige en el art. 14.
- Modelo de Claude: el más barato que redacte bien (Haiku).
- Modelo usuario/empresa: un usuario pertenece a una empresa (sin invitaciones de equipo en diciembre).
- La cadena de hashes de auditoría es una por empresa.
- La web y la API se sirven bajo el mismo dominio (proxy de Next.js) para que la cookie de sesión funcione en Railway.
