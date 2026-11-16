import type { Problem } from "./types";

const WRITE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

let unauthorizedHandler: () => void = () => {};

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;
  readonly fieldErrors: Record<string, string>;

  // Crea el error con el estado HTTP y el problem details que devolvió la API.
  constructor(status: number, problem: Problem | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.fieldErrors = problem?.errors ?? {};
  }
}

export type ApiInit = RequestInit & { json?: unknown };

// Registra qué hacer cuando la API responde 401 (por ejemplo, ir al login).
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler;
}

// Lee el token CSRF de la cookie XSRF-TOKEN que pone la API.
function csrfCookie(): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return match ? decodeURIComponent(match.slice("XSRF-TOKEN=".length)) : null;
}

// Pide a la API un token CSRF nuevo, que llega como cookie.
async function refreshCsrf(): Promise<void> {
  await fetch("/api/auth/csrf", { credentials: "same-origin" });
}

// Hace la petición con la cookie de sesión y, si escribe, con la cabecera CSRF.
async function send(path: string, init: ApiInit, write: boolean): Promise<Response> {
  const { json, ...rest } = init;
  const headers = new Headers(rest.headers);
  headers.set("Accept", "application/json");
  if (json !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (write) {
    const token = csrfCookie();
    if (token) {
      headers.set("X-XSRF-TOKEN", token);
    }
  }
  return fetch(path, {
    ...rest,
    headers,
    credentials: "same-origin",
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });
}

// Lee el problem details de una respuesta de error, o null si no trae JSON.
async function problemOf(response: Response): Promise<Problem | null> {
  try {
    return (await response.json()) as Problem;
  } catch {
    return null;
  }
}

// Hace la petición y, ante un 403 en una escritura, renueva el token CSRF y reintenta una sola vez.
async function request(path: string, init: ApiInit): Promise<Response> {
  const write = WRITE_METHODS.has((init.method ?? "GET").toUpperCase());
  if (write && !csrfCookie()) {
    await refreshCsrf();
  }
  let response = await send(path, init, write);
  if (write && response.status === 403) {
    await refreshCsrf();
    response = await send(path, init, write);
  }
  if (response.status === 401) {
    unauthorizedHandler();
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemOf(response));
  }
  return response;
}

// Llama a la API y devuelve el cuerpo JSON, o undefined si la respuesta no tiene cuerpo.
export async function apiFetch<T>(path: string, init: ApiInit = {}): Promise<T> {
  const response = await request(path, init);
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// Descarga un archivo de la API y devuelve su contenido con el nombre que indica Content-Disposition.
export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await request(path, {});
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), filename: match ? decodeURIComponent(match[1]) : "download" };
}

// Guarda en el equipo del usuario un archivo descargado.
export function saveFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
