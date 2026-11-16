import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiDownload, apiFetch, onUnauthorized } from "./api";

type Call = { url: string; init: RequestInit };

// Crea una respuesta HTTP con cuerpo JSON opcional.
function respond(status: number, body?: unknown, headers: Record<string, string> = {}): Response {
  const init = { status, headers: { "Content-Type": "application/json", ...headers } };
  return new Response(body === undefined ? null : JSON.stringify(body), init);
}

// Reemplaza fetch por uno que contesta en orden y guarda las llamadas.
function mockFetch(...responses: Response[]): Call[] {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, init });
      return responses.shift() ?? respond(500);
    }),
  );
  return calls;
}

// Devuelve una cabecera de una llamada registrada.
function header(call: Call, name: string): string | null {
  return new Headers(call.init.headers).get(name);
}

describe("apiFetch", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    onUnauthorized(() => {});
  });

  it("sendsCsrfHeaderOnWrites", async () => {
    document.cookie = "XSRF-TOKEN=tok-1; path=/";
    const calls = mockFetch(respond(201, { id: "p1" }));

    const result = await apiFetch<{ id: string }>("/api/products", { method: "POST", json: { name: "Lock" } });

    expect(result.id).toBe("p1");
    expect(calls).toHaveLength(1);
    expect(header(calls[0], "X-XSRF-TOKEN")).toBe("tok-1");
    expect(header(calls[0], "Content-Type")).toBe("application/json");
    expect(calls[0].init.body).toBe(JSON.stringify({ name: "Lock" }));
    expect(calls[0].init.credentials).toBe("same-origin");
  });

  it("fetchesCsrfWhenCookieMissing", async () => {
    const calls = mockFetch(respond(204), respond(204));

    await apiFetch("/api/auth/logout", { method: "POST" });

    expect(calls.map((c) => c.url)).toEqual(["/api/auth/csrf", "/api/auth/logout"]);
  });

  it("noCsrfOnGet", async () => {
    document.cookie = "XSRF-TOKEN=tok-1; path=/";
    const calls = mockFetch(respond(200, []));

    await apiFetch("/api/products");

    expect(header(calls[0], "X-XSRF-TOKEN")).toBeNull();
  });

  it("retriesOnceWithFreshTokenOn403", async () => {
    document.cookie = "XSRF-TOKEN=stale; path=/";
    const calls = mockFetch(respond(403, { status: 403 }), respond(204), respond(200, { ok: true }));

    const result = await apiFetch<{ ok: boolean }>("/api/findings/f1/confirm", { method: "POST" });

    expect(result.ok).toBe(true);
    expect(calls.map((c) => c.url)).toEqual(["/api/findings/f1/confirm", "/api/auth/csrf", "/api/findings/f1/confirm"]);
  });

  it("secondForbiddenIsReturnedAsError", async () => {
    document.cookie = "XSRF-TOKEN=tok; path=/";
    mockFetch(respond(403, { status: 403, detail: "no" }), respond(204), respond(403, { status: 403, detail: "no" }));

    const error = await apiFetch("/api/products", { method: "POST", json: {} }).catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 403 });
    expect((error as ApiError).message).toBe("Read-only demo — changes are disabled.");
  });

  it("mapsProblemFieldErrors", async () => {
    mockFetch(respond(400, { title: "Invalid request", status: 400, detail: "bad", errors: { sentAt: "must not be in the future" } }));

    const error = await apiFetch("/api/x").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).fieldErrors.sentAt).toBe("must not be in the future");
    expect((error as ApiError).message).toBe("bad");
  });

  it("callsUnauthorizedHandlerOn401", async () => {
    const handler = vi.fn();
    onUnauthorized(handler);
    mockFetch(respond(401, { status: 401 }));

    await expect(apiFetch("/api/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledOnce();
  });

  it("returnsUndefinedOn204", async () => {
    mockFetch(respond(204));

    expect(await apiFetch("/api/x")).toBeUndefined();
  });

  it("downloadReadsFilename", async () => {
    mockFetch(
      new Response("# Early warning", {
        status: 200,
        headers: { "Content-Disposition": 'attachment; filename="smart-lock-early_warning.md"' },
      }),
    );

    const file = await apiDownload("/api/events/e/reports/EARLY_WARNING/export?format=markdown");

    expect(file.filename).toBe("smart-lock-early_warning.md");
    expect(await file.blob.text()).toBe("# Early warning");
  });
});
