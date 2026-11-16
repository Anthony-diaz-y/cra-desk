"use client";

import { cloneElement, createContext, isValidElement, useContext, useEffect, type ReactElement, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiFetch, onUnauthorized } from "./api";
import { loginRedirect } from "./routes";
import type { Me } from "./types";

export type SessionValue = { me: Me | undefined; isDemo: boolean; isLoading: boolean };

export const SessionContext = createContext<SessionValue>({ me: undefined, isDemo: false, isLoading: true });

// Carga quién es el usuario y manda al login cuando la API responde 401.
export function SessionProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    onUnauthorized(() => router.replace(loginRedirect(pathname)));
    return () => onUnauthorized(() => {});
  }, [router, pathname]);
  const { data, isLoading } = useQuery({
    queryKey: ["me"],
    queryFn: () => apiFetch<Me>("/api/auth/me"),
    staleTime: 5 * 60_000,
  });
  return (
    <SessionContext.Provider value={{ me: data, isDemo: data?.role === "DEMO", isLoading }}>{children}</SessionContext.Provider>
  );
}

// Devuelve el usuario de la sesión, si es la demo y si todavía se está cargando.
export function useSession(): SessionValue {
  return useContext(SessionContext);
}

// Deshabilita su botón hijo en la demo de solo lectura y explica por qué.
export function CanWrite({ children }: { children: ReactElement<{ disabled?: boolean }> }) {
  const { isDemo } = useSession();
  if (!isDemo || !isValidElement(children)) {
    return children;
  }
  return (
    <span title="Read-only demo" className="inline-flex cursor-not-allowed">
      {cloneElement(children, { disabled: true })}
      <span className="sr-only">Read-only demo</span>
    </span>
  );
}
