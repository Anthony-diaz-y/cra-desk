"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Botón "Try the demo": abre la sesión de solo lectura y lleva a la aplicación.
export function TryDemoButton({ className, label = "Try the demo" }: { className?: string; label?: string }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  // Pide la sesión demo a la API y navega al panel.
  async function start() {
    setLoading(true);
    try {
      await apiFetch("/api/auth/demo", { method: "POST" });
      router.push("/app");
    } catch (e) {
      toast.error(e instanceof ApiError && e.status === 404 ? "Demo is not available" : "Could not open the demo");
      setLoading(false);
    }
  }

  return (
    <Button onClick={start} disabled={loading} className={cn("h-11 gap-2 px-5 text-base bg-brand text-brand-foreground hover:bg-brand/90", className)}>
      {loading ? <Loader2 className="size-4 animate-spin" /> : null}
      {label}
      {!loading && <ArrowRight className="size-4" />}
    </Button>
  );
}
