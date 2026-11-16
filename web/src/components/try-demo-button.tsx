"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Botón "Try the demo": abre la sesión de solo lectura y lleva a la aplicación.
export function TryDemoButton({ className, label = "Try the demo" }: { className?: string; label?: string }) {
  const router = useRouter();
  const client = useQueryClient();
  const [loading, setLoading] = useState(false);

  // Pide la sesión demo a la API y navega al panel.
  async function start() {
    setLoading(true);
    try {
      await apiFetch("/api/auth/demo", { method: "POST" });
      client.clear();
      router.push("/app");
    } catch (e) {
      toast.error(e instanceof ApiError && e.status === 404 ? "Demo is not available" : "Could not open the demo");
      setLoading(false);
    }
  }

  return (
    <Button onClick={start} disabled={loading} className={cn("group h-11 gap-2 bg-brand px-5 text-base text-brand-foreground shadow-sm shadow-brand/20 transition-all hover:-translate-y-0.5 hover:bg-brand/90 hover:shadow-md", className)}>
      {loading ? <Loader2 className="size-4 animate-spin" /> : null}
      {label}
      {!loading && <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />}
    </Button>
  );
}
