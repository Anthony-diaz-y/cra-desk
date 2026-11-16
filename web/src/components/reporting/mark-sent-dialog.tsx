"use client";

import { useState, type FormEvent } from "react";
import { Loader2, Send } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { CanWrite } from "@/lib/session";
import type { Report, ReportType } from "@/lib/types";
import { Field } from "@/components/auth/field";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

// Devuelve la hora local actual en el formato de un input datetime-local.
function localNow(): string {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

// Botón y diálogo para marcar el reporte como enviado a ENISA con la hora y la referencia.
export function MarkSentDialog({ eventId, type, onSent }: { eventId: string; type: ReportType; onSent: (report: Report) => void }) {
  const [open, setOpen] = useState(false);
  const [sentAt, setSentAt] = useState("");
  const [opened, setOpened] = useState("");
  const [reference, setReference] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);

  // Abre el diálogo con la hora actual ya cargada.
  function show() {
    const now = localNow();
    setSentAt(now);
    setOpened(now);
    setErrors({});
    setOpen(true);
  }

  // Envía la hora (convertida a UTC) y la referencia; si no se cambió la hora propuesta, la API usa su hora actual.
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSending(true);
    setErrors({});
    try {
      const saved = await apiFetch<Report>(`/api/events/${eventId}/reports/${type}/sent`, {
        method: "POST",
        json: { sentAt: sentAt && sentAt !== opened ? new Date(sentAt).toISOString() : null, enisaReference: reference.trim() || null },
      });
      setOpen(false);
      onSent(saved);
    } catch (e) {
      setErrors(e instanceof ApiError ? (Object.keys(e.fieldErrors).length ? e.fieldErrors : { form: e.message }) : { form: "Could not mark it as sent." });
    } finally {
      setSending(false);
    }
  }

  return (
    <>
      <CanWrite>
        <Button onClick={show} className="gap-1.5 bg-brand text-brand-foreground hover:bg-brand/90"><Send className="size-4" /> Mark as sent</Button>
      </CanWrite>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Mark as sent to ENISA</DialogTitle>
            <DialogDescription>Record when you submitted this report on ENISA&apos;s platform. This stops the clock and can&apos;t be undone.</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} noValidate className="grid gap-4">
            <Field id="sent-at" label="Sent at (your local time)" type="datetime-local" value={sentAt} max={localNow()} onChange={(e) => setSentAt(e.target.value)} error={errors.sentAt} />
            <Field id="sent-reference" label="ENISA reference (optional)" maxLength={100} value={reference} onChange={(e) => setReference(e.target.value)} error={errors.enisaReference} />
            {errors.form && <p role="alert" className="text-sm text-danger">{errors.form}</p>}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={sending}>
                {sending && <Loader2 className="size-4 animate-spin" />}
                Confirm sent
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
