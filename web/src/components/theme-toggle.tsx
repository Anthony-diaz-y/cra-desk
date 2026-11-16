"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";

const ORDER = ["light", "dark", "system"] as const;

// Suscripción vacía: solo sirve para saber si ya se está renderizando en el navegador.
function subscribeNothing() {
  return () => {};
}

// Botón que alterna entre tema claro, oscuro y el del sistema.
export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(subscribeNothing, () => true, () => false);
  const current = mounted ? ((theme as (typeof ORDER)[number]) ?? "system") : "system";
  const next = ORDER[(ORDER.indexOf(current) + 1) % ORDER.length];
  const Icon = current === "light" ? Sun : current === "dark" ? Moon : Monitor;
  return (
    <Button variant="ghost" size="icon" aria-label={`Theme: ${current}. Switch to ${next}`} onClick={() => setTheme(next)}>
      <Icon className="size-4" />
    </Button>
  );
}
