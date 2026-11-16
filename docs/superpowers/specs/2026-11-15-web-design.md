# CRA Desk — Diseño de la web (Etapa 7)

Fecha: 2026-11-15 · Estado: listo para aprobación del usuario

Documento padre: `docs/superpowers/specs/2026-11-13-cra-desk-design.md` (secciones 2, 4 y 7). Este documento solo concreta la Etapa 7.

## 1. Intención y porqué

La API (etapas 0 a 6) ya hace todo el trabajo: productos, SBOM, vulnerabilidades, hallazgos, relojes del CRA, borradores, aprobación, "enviado a ENISA", exportación y auditoría. Falta **verlo**. La web es la cara del portafolio: quien la abra (un reclutador, un cliente o un fabricante curioso) tiene que entender en segundos qué problema resuelve y, en pocos minutos, ver el flujo funcionando con la cuenta demo, sin instalar nada.

Lo que pidió el usuario, literal: "algo bonito y elegante con animaciones y profesional y seguro". Dejó a criterio de Claude todo lo demás.

## 2. Metas, no-metas y anti-metas

**Metas:**

- **Portada pública** (`/`) que cuente el problema: los tres plazos del CRA (24 h / 72 h / 14 d) con un reloj animado, cómo funciona CRA Desk en tres pasos (SBOM → hallazgo → reporte), botones **"Try the demo"** y **"Request access"** (formulario que llama a `POST /api/access-requests`), y el aviso de que es una herramienta de apoyo, no asesoría legal.
- **Login** (`/login`) con email y contraseña, más el botón de demo. **Aceptar invitación** (`/invite`) con código, email, contraseña y nombre de la empresa.
- **Aplicación** detrás del login, con barra lateral:
  - **Overview / Deadlines** (`/app`): eventos reportables con sus tres relojes (cuenta regresiva en vivo, color por estado: pendiente, por vencer, vencido, enviado a tiempo o tarde) y el número de hallazgos por revisar.
  - **Products** (`/app/products`, `/app/products/[id]`, `/app/products/[id]/versions/[versionId]`): crear, editar y borrar productos (borrar solo si no tiene versiones: la API responde 409); crear versiones (la API no permite editarlas ni borrarlas, y esta etapa no lo agrega); en la versión, subir el SBOM (arrastrar un archivo CycloneDX JSON), ver el resumen, la lista paginada de componentes, el estado del escaneo y las vulnerabilidades (KEV primero), y pedir un nuevo escaneo.
  - **Findings** (`/app/findings`): pestañas Pending / Confirmed / Dismissed; confirmar (abre el evento y lleva a él) o descartar con motivo.
  - **Event** (`/app/events/[id]`): los tres reportes con su reloj; para cada uno, el **editor**: generar borrador (con IA o con plantilla; el botón de IA solo aparece si hay un proveedor configurado y muestra cuántos borradores con IA quedan hoy), ver quién lo generó y el `aiError` si la IA falló, editar los campos del art. 14, aprobar, marcar enviado (fecha/hora y referencia de ENISA) y exportar JSON o Markdown.
  - **Audit** (`/app/audit`): tabla paginada de la auditoría y un botón "Verify chain" que muestra si la cadena es válida o la primera entrada alterada.
  - **API keys** (`/app/settings`): crear (mostrar la clave una sola vez con botón copiar), listar y revocar; con el comando de ejemplo de la CLI.
- **Modo demo:** la sesión demo ve todo y **no ve** los botones que escriben (aparecen deshabilitados con el texto "Read-only demo"). Una franja arriba avisa que es la demo y ofrece "Request access".
- **Bonita y elegante:** tipografía cuidada, buen espaciado, paleta sobria con un color de acento, modo claro y oscuro (sigue al sistema, con interruptor), animaciones sutiles: aparición al hacer scroll en la portada, transiciones entre páginas y paneles, números de los relojes que cambian con suavidad, estados vacíos y de carga con esqueletos. Todo respeta `prefers-reduced-motion`.
- **Responsive:** usable en el teléfono (barra lateral que se pliega) y accesible (teclado, foco visible, contraste AA, etiquetas).

**No-metas (después de esta etapa):**

- Exportación PDF (sigue siendo JSON/Markdown, como la API).
- Registro abierto, recuperación de contraseña, páginas legales (enero, según el diseño padre).
- Internacionalización: la interfaz está **en inglés** (mercado de la UE; la API y el código ya están en inglés).
- Gráficos de analítica o paneles de métricas.

**Anti-metas:**

- Que parezca una plantilla genérica de "dashboard" o asesoría legal.
- Animaciones que estorben, mareen o hagan esperar.
- Guardar tokens o claves en el navegador (localStorage), o desactivar la protección CSRF.
- Código que el usuario no pueda explicar en una entrevista.

## 3. Restricciones

- Stack fijado por el diseño padre: **Next.js (App Router) + TypeScript + Tailwind** en `web/`.
- La web y la API van bajo **el mismo dominio**: Next reenvía `/api/*` y `/actuator/health` a la API (rewrites con la URL de la API en una variable de entorno, `CRADESK_API_URL`, por defecto `http://localhost:8080`). Así la cookie de sesión `JSESSIONID` (HttpOnly, SameSite=Lax) y la cookie `XSRF-TOKEN` funcionan sin CORS.
- La API no cambia en esta etapa salvo lo imprescindible para la web, cada cambio con su test en la API. El único previsto: `GET /api/reporting/ai` → `{available, provider, model, dailyLimit, remainingToday}` (para miembros y demo; sin claves ni secretos), para que el editor sea honesto sobre IA o plantilla. *(Decisión de Claude.)*
- Presupuesto casi cero y modo de trabajo "avance directo" (Claude implementa y explica qué leer).
- Reglas del usuario: solo un comentario de una línea en español encima de cada función; commits sin co-autor de Claude; el usuario hace el push.

## 4. Enfoques considerados

| Enfoque | A favor | En contra | Decisión |
|---|---|---|---|
| **A. Portada + aplicación en el mismo Next.js** | Cuenta la historia y la muestra funcionando; un solo proyecto y un solo despliegue | Un poco más de trabajo que solo la app | **Elegido (criterio de Claude; el usuario delegó)** |
| B. Solo la aplicación (login con botón demo) | Más rápido | Quien llega no entiende el problema antes de entrar; pobre para portafolio | Descartado |
| C. Sitio de marketing separado + aplicación | Separación clara | Dos proyectos y despliegues: sobreingeniería | Descartado |

## 5. Cómo se construye (decisiones de Claude)

- **Componentes:** shadcn/ui (componentes Radix copiados al repo, accesibles) sobre Tailwind. Íconos lucide. Tipografía con `next/font` (por ejemplo Inter o Geist para texto y una monoespaciada para relojes, CVE y hashes).
- **Animación:** la librería Motion (antes Framer Motion), solo en componentes de cliente; nada de animaciones en bucle salvo los segundos del reloj.
- **Datos:** las páginas de la aplicación son componentes de cliente que llaman a la API por el mismo dominio con un cliente `fetch` propio y TanStack Query (caché, reintentos, invalidación tras confirmar, aprobar, etc.). El cliente:
  - envía `credentials: 'same-origin'` y, en métodos que escriben, la cabecera `X-XSRF-TOKEN` leída de la cookie `XSRF-TOKEN`; si no hay cookie, primero llama a `GET /api/auth/csrf`;
  - convierte los errores RFC 9457 (`detail`, `errors` por campo) en mensajes en los formularios;
  - ante un 401 manda al login; ante un 403 muestra "not allowed" (la demo).
- **Protección de rutas:** un `middleware` de Next redirige `/app/*` a `/login` si no hay cookie de sesión; la fuente de verdad sigue siendo `GET /api/auth/me` (rol, empresa), que la app consulta al cargar.
- **Seguridad del navegador:** `Content-Security-Policy` con **nonce por petición** generado en el `middleware` para los scripts (`script-src 'self' 'nonce-…' 'strict-dynamic'`, sin `'unsafe-inline'` ni `'unsafe-eval'` en producción). Eso hace que todas las páginas, incluida la portada, se rendericen en el servidor en cada petición; se acepta ese costo por la seguridad. Para estilos se permite `'unsafe-inline'`, porque Motion y Radix ponen estilos en línea y el riesgo de inyectar estilos es mucho menor que el de scripts. *(Decisión de Claude.)* Resto de cabeceras (en el middleware o `next.config`): `connect-src 'self'`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'`. Nunca se usa `dangerouslySetInnerHTML`; el Markdown exportado se descarga como archivo, no se renderiza como HTML. La clave de API recién creada solo vive en memoria del componente.
- **Relojes:** se calculan en el navegador a partir de `dueAt` y del estado que manda la API; la cuenta regresiva corre cada segundo y el estado se refresca de la API cada minuto.
- **Pruebas:**
  - unitarias y de componentes con Vitest + Testing Library (cliente de API, CSRF, formato de relojes, formularios y estados demo);
  - extremo a extremo con Playwright contra la API real local (con la base de datos de Docker): portada → "Try the demo" → ver el evento Log4Shell; y con un usuario invitado: subir SBOM → confirmar hallazgo → borrador con plantilla → aprobar → enviado → exportar → verificar auditoría.
  - Para que sean rápidas, repetibles y sirvan después en CI (Etapa 8), las pruebas extremo a extremo **no usan OSV ni CISA reales**: un servidor falso pequeño (Node) responde como OSV y como el feed KEV con Log4Shell, y la API arranca apuntándole con `CRADESK_OSV_BASE_URL` y `CRADESK_KEV_URL`, con un token de admin de prueba (`CRADESK_ADMIN_TOKEN`) y el proveedor de IA en `template`. Cada corrida usa un email y una empresa nuevos (con marca de tiempo), así no hace falta limpiar. *(Decisión de Claude.)*
- **Script de desarrollo:** `npm run dev` en `web/` con la API corriendo; el README explica cómo levantar ambos.

## 6. Decisiones tomadas tras la revisión del diseño

El usuario dejó el cómo a criterio de Claude; estas cuatro las tomó Claude después de la revisión:

1. **Versiones solo se crean** (sin editar ni borrar), porque la API no lo permite y agregarlo no aporta a lo que se quiere mostrar. Los productos sí se editan y se borran.
2. **Pruebas extremo a extremo con OSV y KEV falsos**, listas para CI.
3. **CSP con nonce para scripts** y estilos en línea permitidos.
4. **Endpoint `GET /api/reporting/ai`** para mostrar si hay IA y cuánto cupo queda.

## 7. Lo que queda a criterio de quien implemente

- La paleta exacta, la tipografía final y los detalles de cada animación, dentro de lo dicho en la sección 2.
- La versión exacta de Next.js, React, Tailwind y librerías (las estables más recientes al empezar).
- La estructura interna de carpetas de `web/` y los nombres de componentes.
- Textos de la portada (en inglés, claros y sin promesas legales).
