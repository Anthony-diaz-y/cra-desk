"use client";

import { useState, type FormEvent } from "react";
import { CheckCircle2, Loader2 } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

type Errors = Record<string, string>;

// Formulario "Request access" que guarda el email en la lista de espera de la API.
export function RequestAccessForm() {
  const [email, setEmail] = useState("");
  const [company, setCompany] = useState("");
  const [message, setMessage] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);

  // Valida el email y envía el formulario a la API.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!email.trim()) {
      setErrors({ email: "Enter your work email." });
      return;
    }
    setSending(true);
    setErrors({});
    try {
      await apiFetch("/api/access-requests", { method: "POST", json: { email: email.trim(), company: company.trim(), message: message.trim() } });
      setDone(true);
    } catch (e) {
      setErrors(e instanceof ApiError && Object.keys(e.fieldErrors).length ? e.fieldErrors : { form: "Something went wrong. Please try again." });
    } finally {
      setSending(false);
    }
  }

  if (done) {
    return (
      <div role="status" className="flex items-center gap-3 rounded-xl border border-success/30 bg-success/10 p-5 text-sm">
        <CheckCircle2 className="size-5 text-success" />
        <span>Thanks — we&apos;ll be in touch soon.</span>
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor="ra-email">Work email</Label>
        <Input id="ra-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
          aria-invalid={!!errors.email} aria-describedby={errors.email ? "ra-email-error" : undefined} className="h-10" />
        {errors.email && <p id="ra-email-error" className="text-sm text-danger">{errors.email}</p>}
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="ra-company">Company</Label>
        <Input id="ra-company" autoComplete="organization" value={company} onChange={(e) => setCompany(e.target.value)} className="h-10" />
        {errors.company && <p className="text-sm text-danger">{errors.company}</p>}
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="ra-message">What do you build? <span className="text-muted-foreground">(optional)</span></Label>
        <Textarea id="ra-message" rows={3} value={message} onChange={(e) => setMessage(e.target.value)} />
        {errors.message && <p className="text-sm text-danger">{errors.message}</p>}
      </div>
      {errors.form && <p className="text-sm text-danger">{errors.form}</p>}
      <Button type="submit" disabled={sending} className="h-10 bg-brand text-brand-foreground hover:bg-brand/90">
        {sending && <Loader2 className="size-4 animate-spin" />}
        Request access
      </Button>
    </form>
  );
}
