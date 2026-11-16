"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { Button } from "@/components/ui/button";

// Muestra el usuario de la sesión y el botón para salir.
export function UserMenu() {
  const router = useRouter();
  const client = useQueryClient();
  const { me, isDemo } = useSession();
  const label = isDemo ? "Demo visitor" : me?.email;
  const initial = label?.trim().charAt(0).toUpperCase() ?? "?";

  // Cierra la sesión en la API, vacía la caché y vuelve a la portada.
  async function logout() {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" });
    } finally {
      client.clear();
      router.replace("/");
    }
  }

  return (
    <div className="flex items-center gap-1.5">
      <span className="hidden max-w-[200px] items-center gap-2 rounded-full border border-border/70 bg-card/60 py-1 pl-1 pr-3 sm:flex">
        <span className="grid size-6 shrink-0 place-items-center rounded-full bg-brand/10 text-[10px] font-semibold text-brand">
          {initial}
        </span>
        <span className="truncate text-xs text-muted-foreground">{label}</span>
      </span>
      <Button variant="ghost" size="sm" onClick={logout} className="gap-1.5 text-muted-foreground hover:text-foreground">
        <LogOut className="size-4" />
        <span className="hidden sm:inline">{isDemo ? "Leave demo" : "Log out"}</span>
        <span className="sr-only sm:hidden">{isDemo ? "Leave demo" : "Log out"}</span>
      </Button>
    </div>
  );
}
