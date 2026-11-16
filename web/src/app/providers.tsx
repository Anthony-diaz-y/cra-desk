"use client";

import { useState, type ReactNode } from "react";
import { MotionConfig } from "motion/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "@/components/theme-provider";
import { Toaster } from "@/components/ui/sonner";

// Envuelve la app con el tema, la caché de datos, la animación que respeta "reducir movimiento" y los avisos.
export function Providers({ children, nonce }: { children: ReactNode; nonce?: string }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false } },
      }),
  );
  return (
    <ThemeProvider nonce={nonce}>
      <QueryClientProvider client={client}>
        <MotionConfig reducedMotion="user">
          {children}
          <Toaster richColors position="top-right" />
        </MotionConfig>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
