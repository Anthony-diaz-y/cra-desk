import type { ReactElement, ReactNode } from "react";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionContext, type SessionValue } from "@/lib/session";
import type { Me } from "@/lib/types";
import { vi } from "vitest";

export const MEMBER: Me = {
  userId: "u-1",
  email: "maker@example.com",
  organizationId: "o-1",
  organizationName: "Maker GmbH",
  role: "MEMBER",
};

export const DEMO: Me = { ...MEMBER, email: null, organizationName: "CRA Desk Demo", role: "DEMO" };

// Crea una caché de datos sin reintentos para los tests.
export function testClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

// Renderiza con la caché indicada (o una nueva) y la sesión indicada; con null simula la sesión cargando.
export function renderWithSession(ui: ReactElement, me: Me | null = MEMBER, client = testClient()) {
  const session: SessionValue = { me: me ?? undefined, isDemo: me?.role === "DEMO", isLoading: me === null };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <SessionContext.Provider value={session}>{children}</SessionContext.Provider>
    </QueryClientProvider>
  );
  return { client, ...render(ui, { wrapper }) };
}

type Route = { method?: string; path: string | RegExp; status?: number; body?: unknown; headers?: Record<string, string> };

// Reemplaza fetch por uno que contesta según método y ruta, y devuelve el espía para revisar las llamadas.
export function mockApi(routes: Route[]) {
  document.cookie = "XSRF-TOKEN=t; path=/";
  const spy = vi.fn(async (url: string, init: RequestInit = {}) => {
    const method = (init.method ?? "GET").toUpperCase();
    const route = routes.find((r) => (r.method ?? "GET") === method && (typeof r.path === "string" ? url === r.path : r.path.test(url)));
    if (!route) {
      return new Response(JSON.stringify({ status: 404, detail: `No mock for ${method} ${url}` }), { status: 404 });
    }
    const body = route.body === undefined ? null : typeof route.body === "string" ? route.body : JSON.stringify(route.body);
    return new Response(body, { status: route.status ?? 200, headers: { "Content-Type": "application/json", ...route.headers } });
  });
  vi.stubGlobal("fetch", spy);
  return spy;
}

// Devuelve las llamadas a fetch con ese método y ruta, con el cuerpo ya interpretado como JSON.
export function callsTo(spy: ReturnType<typeof mockApi>, method: string, path: string | RegExp) {
  return spy.mock.calls
    .map(([url, init]) => ({ url: url as string, init: (init ?? {}) as RequestInit }))
    .filter((c) => (c.init.method ?? "GET").toUpperCase() === method && (typeof path === "string" ? c.url === path : path.test(c.url)))
    .map((c) => ({ ...c, json: typeof c.init.body === "string" ? JSON.parse(c.init.body) : c.init.body }));
}
