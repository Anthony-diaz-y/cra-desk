import { vi } from "vitest";

export const router = { push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), back: vi.fn(), prefetch: vi.fn() };
export const navigation = { pathname: "/app", search: "" };

vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => navigation.pathname,
  useSearchParams: () => new URLSearchParams(navigation.search),
  useParams: () => ({}),
}));

// Deja el router simulado y la ruta actual como al principio.
export function resetNavigation(): void {
  Object.values(router).forEach((fn) => fn.mockReset());
  navigation.pathname = "/app";
  navigation.search = "";
}
