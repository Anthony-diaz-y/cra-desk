"use client";

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Field } from "./field";

const MIN_PASSWORD = 12;

// Formulario para aceptar una invitación: crea la empresa, la cuenta y abre la sesión.
export function InviteForm() {
  const router = useRouter();
  const params = useSearchParams();
  const client = useQueryClient();
  const [code, setCode] = useState(params.get("code") ?? "");
  const [email, setEmail] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);

  // Valida la contraseña y envía la invitación a la API.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (password.length < MIN_PASSWORD) {
      setErrors({ password: `Use at least ${MIN_PASSWORD} characters.` });
      return;
    }
    setSending(true);
    setErrors({});
    try {
      await apiFetch("/api/auth/accept-invitation", {
        method: "POST",
        json: { code: code.trim(), email: email.trim(), password, organizationName: organizationName.trim() },
      });
      client.clear();
      router.replace("/app");
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(Object.keys(e.fieldErrors).length ? e.fieldErrors : { form: e.message });
      } else {
        setErrors({ form: "Could not create the account. Please try again." });
      }
      setSending(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <Field id="invite-code" label="Invitation code" required value={code} onChange={(e) => setCode(e.target.value)} error={errors.code} />
      <Field id="invite-email" label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
      <Field id="invite-org" label="Company name" autoComplete="organization" required value={organizationName} onChange={(e) => setOrganizationName(e.target.value)} error={errors.organizationName} />
      <Field id="invite-password" label="Password" type="password" autoComplete="new-password" required value={password}
        onChange={(e) => setPassword(e.target.value)} error={errors.password} hint={`At least ${MIN_PASSWORD} characters.`} />
      {errors.form && <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{errors.form}</p>}
      <Button type="submit" disabled={sending} className="h-10">
        {sending && <Loader2 className="size-4 animate-spin" />}
        Create account
      </Button>
    </form>
  );
}
