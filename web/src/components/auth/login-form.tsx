"use client";

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { safeNext } from "@/lib/routes";
import { Button } from "@/components/ui/button";
import { Field } from "./field";

// Formulario de login con email y contraseña que vuelve a la página pedida.
export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const client = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  // Envía las credenciales y entra a la app si son correctas.
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setError(null);
    try {
      await apiFetch("/api/auth/login", { method: "POST", json: { email: email.trim(), password } });
      client.clear();
      router.replace(safeNext(params.get("next")));
    } catch (e) {
      setError(e instanceof ApiError && (e.status === 401 || e.status === 429) ? e.message : "Could not sign in. Please try again.");
      setSending(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-4">
      <Field id="login-email" label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      <Field id="login-password" label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      {error && <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</p>}
      <Button type="submit" disabled={sending} className="h-10">
        {sending && <Loader2 className="size-4 animate-spin" />}
        Sign in
      </Button>
    </form>
  );
}
