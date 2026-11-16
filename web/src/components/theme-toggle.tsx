"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Suscripción vacía: solo sirve para saber si ya se está renderizando en el navegador.
function subscribeNothing() {
  return () => {};
}

// Botón que alterna entre tema claro y oscuro. El icono muestra siempre el modo activo: sol o luna.
export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const mounted = useSyncExternalStore(subscribeNothing, () => true, () => false);
  const isDark = mounted && resolvedTheme === "dark";
  const label = isDark ? "Switch to light theme" : "Switch to dark theme";
  return (
    <Button variant="ghost" size="icon" aria-label={label} title={label} onClick={() => setTheme(isDark ? "light" : "dark")}>
      <span className="relative grid size-4 place-items-center">
        <Sun
          className={cn(
            "size-4 transition-all duration-300",
            isDark ? "-rotate-90 scale-0 opacity-0" : "rotate-0 scale-100 opacity-100",
          )}
        />
        <Moon
          className={cn(
            "absolute size-4 transition-all duration-300",
            isDark ? "rotate-0 scale-100 opacity-100" : "rotate-90 scale-0 opacity-0",
          )}
        />
      </span>
    </Button>
  );
}
