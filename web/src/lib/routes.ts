// Devuelve el destino tras el login solo si es una ruta de la app; si no, el panel (evita redirecciones abiertas).
export function safeNext(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) {
    return "/app";
  }
  return next === "/app" || next.startsWith("/app/") || next.startsWith("/app?") ? next : "/app";
}

// Arma la ruta del login que vuelve a esta página después de entrar.
export function loginRedirect(pathname: string): string {
  return `/login?next=${encodeURIComponent(pathname)}`;
}
