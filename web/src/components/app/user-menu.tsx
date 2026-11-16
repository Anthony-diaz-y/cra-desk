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
    <div className="flex items-center gap-2">
      <span className="hidden text-sm text-muted-foreground sm:inline">{isDemo ? "Demo visitor" : me?.email}</span>
      <Button variant="ghost" size="sm" onClick={logout} className="gap-1.5">
        <LogOut className="size-4" />
        {isDemo ? "Leave demo" : "Log out"}
      </Button>
    </div>
  );
}
