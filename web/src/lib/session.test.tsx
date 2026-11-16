import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { navigation, resetNavigation, router } from "@/test/navigation-mock";
import { mockApi } from "@/test/render";
import { SessionProvider } from "./session";
import { loginRedirect, safeNext } from "./routes";

describe("session", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("unauthorizedRedirectsToLogin", async () => {
    navigation.pathname = "/app/findings";
    mockApi([{ path: "/api/auth/me", status: 401, body: { status: 401 } }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <SessionProvider>
          <p>app</p>
        </SessionProvider>
      </QueryClientProvider>,
    );

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fapp%2Ffindings"));
  });

  it("unauthorizedClearsCachedData", async () => {
    mockApi([{ path: "/api/auth/me", status: 401, body: { status: 401 } }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(["events"], [{ id: "someone-elses-event" }]);

    render(
      <QueryClientProvider client={client}>
        <SessionProvider>
          <p>app</p>
        </SessionProvider>
      </QueryClientProvider>,
    );

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalled());
    expect(client.getQueryData(["events"])).toBeUndefined();
  });

  it("safeNextOnlyAllowsAppPaths", () => {
    expect(safeNext("/app/events/1")).toBe("/app/events/1");
    expect(safeNext("https://evil.example")).toBe("/app");
    expect(safeNext("//evil.example/app")).toBe("/app");
    expect(safeNext("/application")).toBe("/app");
    expect(safeNext(null)).toBe("/app");
  });

  it("loginRedirectKeepsThePath", () => {
    expect(loginRedirect("/app/audit")).toBe("/login?next=%2Fapp%2Faudit");
  });
});
