"use client";

import { useState, type FormEvent } from "react";
import { ArrowRight, Building2, CheckCircle2, Loader2, Mail } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

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
      <div role="status" className="flex flex-col items-center rounded-2xl border border-success/25 bg-success/5 px-6 py-10 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-success/10 text-success">
          <CheckCircle2 className="size-6" />
        </span>
        <p className="mt-4 font-medium">Thanks — we&apos;ll be in touch soon.</p>
        <p className="mt-1 text-sm text-muted-foreground">Your request is on the early-access list.</p>
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor="ra-email">Work email</Label>
        <div className="relative">
          <Mail className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <Input id="ra-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
            aria-invalid={!!errors.email} aria-describedby={errors.email ? "ra-email-error" : undefined}
            className={cn("h-10 bg-background/60 pl-9", errors.email && "border-danger/60")} placeholder="you@company.com" />
        </div>
        {errors.email && <p id="ra-email-error" className="text-sm text-danger">{errors.email}</p>}
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="ra-company">Company</Label>
        <div className="relative">
          <Building2 className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground/70" />
          <Input id="ra-company" autoComplete="organization" value={company} onChange={(e) => setCompany(e.target.value)}
            className="h-10 bg-background/60 pl-9" placeholder="Your company" />
        </div>
        {errors.company && <p className="text-sm text-danger">{errors.company}</p>}
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="ra-message">
          What do you build? <span className="text-muted-foreground">(optional)</span>
        </Label>
        <Textarea id="ra-message" rows={3} value={message} onChange={(e) => setMessage(e.target.value)}
          className="resize-none bg-background/60" placeholder="Hardware, firmware, IoT, installable software…" />
        {errors.message && <p className="text-sm text-danger">{errors.message}</p>}
      </div>
      {errors.form && <p role="alert" className="text-sm text-danger">{errors.form}</p>}
      <Button type="submit" disabled={sending} className="group h-11 gap-2 bg-brand text-brand-foreground shadow-sm shadow-brand/20 hover:bg-brand/90">
        {sending && <Loader2 className="size-4 animate-spin" />}
        Request access
        {!sending && <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />}
      </Button>
      <p className="text-center text-[11px] leading-relaxed text-muted-foreground">
        No newsletter. We only use this to reply about early access.
      </p>
    </form>
  );
}
