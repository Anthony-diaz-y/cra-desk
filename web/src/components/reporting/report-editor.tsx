"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, FileText, Loader2, Save, Sparkles, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { fieldLabel, dateTime } from "@/lib/format";
import { useSession } from "@/lib/session";
import type { AiStatus, Report } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { ExportButtons } from "./export-menu";
import { MarkSentDialog } from "./mark-sent-dialog";

const MAX_FIELD = 4000;
const PENDING_TEXT = "TO BE COMPLETED";

// Devuelve quién generó el borrador en palabras para la persona.
function authorLabel(report: Report): string {
  if (!report.generatedBy) {
    return "";
  }
  if (report.generatedBy === "TEMPLATE") {
    return "Drafted from the template";
  }
  if (report.generatedBy === "MANUAL") {
    return "Edited by you";
  }
  return "Drafted with AI";
}

// Editor de un reporte del art. 14: generar borrador, editar campos, aprobar, marcar enviado y exportar.
export function ReportEditor({ eventId, report, ai }: { eventId: string; report: Report; ai: AiStatus | undefined }) {
  const client = useQueryClient();
  const { isDemo: demo, isLoading: sessionLoading } = useSession();
  const isDemo = demo || sessionLoading;
  const url = `/api/events/${eventId}/reports/${report.type}`;
  const [values, setValues] = useState<Record<string, string>>(report.content ?? {});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [confirmAi, setConfirmAi] = useState<boolean | null>(null);
  const sent = report.sentAt !== null;
  const readOnly = sent || isDemo;
  const dirty = JSON.stringify(values) !== JSON.stringify(report.content ?? {});
  const fields = Object.keys(values);

  // Guarda el reporte devuelto por la API en la caché y refresca el evento.
  async function applied(updated: Report) {
    client.setQueryData(["report", eventId, report.type], updated);
    setValues(updated.content ?? {});
    await Promise.all([
      client.invalidateQueries({ queryKey: ["event", eventId] }),
      client.invalidateQueries({ queryKey: ["events"] }),
      client.invalidateQueries({ queryKey: ["ai-status"] }),
      client.invalidateQueries({ queryKey: ["audit"] }),
    ]);
  }

  // Ejecuta una acción del editor mostrando su estado de carga y los errores de la API.
  async function run(name: string, action: () => Promise<Report>, success: string) {
    setBusy(name);
    setErrors({});
    setNotice(null);
    try {
      await applied(await action());
      toast.success(success);
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) {
        setNotice("Daily AI limit reached — use the template instead, or try again tomorrow.");
      } else if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        setErrors(e.fieldErrors);
      } else {
        setNotice(e instanceof Error ? e.message : "Something went wrong.");
      }
    } finally {
      setBusy(null);
    }
  }

  // Pide un borrador nuevo, con IA o con la plantilla.
  function generate(useAi: boolean) {
    setConfirmAi(null);
    void run(useAi ? "ai" : "template", () => apiFetch<Report>(`${url}/draft?ai=${useAi}`, { method: "POST" }), "Draft ready");
  }

  // Pide confirmación antes de reemplazar un borrador que ya existe.
  function requestDraft(useAi: boolean) {
    if (report.content) {
      setConfirmAi(useAi);
    } else {
      generate(useAi);
    }
  }

  return (
    <div className="grid gap-5">
      {!sent && (
        <div className="flex flex-wrap items-center gap-2">
          {ai?.available && (
            <Button onClick={() => requestDraft(true)} disabled={isDemo || !!busy || ai.remainingToday === 0} className="gap-1.5 bg-brand text-brand-foreground hover:bg-brand/90">
              {busy === "ai" ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
              Generate with AI ({ai.remainingToday} left today)
            </Button>
          )}
          <Button variant="outline" onClick={() => requestDraft(false)} disabled={isDemo || !!busy} className="gap-1.5">
            {busy === "template" ? <Loader2 className="size-4 animate-spin" /> : <FileText className="size-4" />}
            Use template
          </Button>
          {report.generatedBy && <span className="text-sm text-muted-foreground">{authorLabel(report)}</span>}
        </div>
      )}

      {report.aiError && !sent && (
        <p role="status" className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 px-3 py-2 text-sm">
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" />
          <span>The AI draft failed ({report.aiError}), so this draft comes from the template.</span>
        </p>
      )}
      {notice && <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{notice}</p>}

      {fields.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          No draft yet. Generate one{ai?.available ? " with AI or" : ""} from the template, then review every field.
        </p>
      ) : (
        <div className="grid gap-4">
          {fields.map((field) => {
            const value = values[field] ?? "";
            const pending = value.trim() === PENDING_TEXT;
            return (
              <div key={field} className="grid gap-1.5">
                <div className="flex items-center justify-between gap-2">
                  <Label htmlFor={`${report.type}-${field}`}>{fieldLabel(field)}</Label>
                  {pending && <span className="rounded-full bg-warning/15 px-2 py-0.5 text-xs font-medium text-foreground">Needs your input</span>}
                </div>
                <Textarea id={`${report.type}-${field}`} value={value} readOnly={readOnly} maxLength={MAX_FIELD} rows={3}
                  aria-invalid={!!errors[field]}
                  onChange={(e) => setValues((current) => ({ ...current, [field]: e.target.value }))}
                  className={cn("leading-relaxed", pending && "border-warning/60 bg-warning/5", readOnly && "bg-muted/30")} />
                <div className="flex justify-between text-xs">
                  <span className="text-danger">{errors[field]}</span>
                  {!readOnly && <span className="text-muted-foreground tabular">{value.length}/{MAX_FIELD}</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {fields.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-4">
          <div className="text-sm text-muted-foreground">
            {sent ? (
              <span className="inline-flex items-center gap-1.5 text-success"><CheckCircle2 className="size-4" /> Sent {dateTime(report.sentAt)}{report.enisaReference ? ` · ${report.enisaReference}` : ""}</span>
            ) : report.draftStatus === "APPROVED" ? (
              <span className="inline-flex items-center gap-1.5 text-success"><CheckCircle2 className="size-4" /> Approved {dateTime(report.approvedAt)}</span>
            ) : dirty ? "Unsaved changes" : "Draft — review it, then approve"}
          </div>
          <div className="flex flex-wrap gap-2">
            {!sent && (
              <Button variant="outline" disabled={isDemo || !dirty || !!busy} className="gap-1.5"
                onClick={() => run("save", () => apiFetch<Report>(url, { method: "PUT", json: { content: values } }), "Draft saved")}>
                {busy === "save" ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} Save
              </Button>
            )}
            {!sent && report.draftStatus !== "APPROVED" && (
              <Button disabled={isDemo || dirty || !!busy} className="gap-1.5"
                onClick={() => run("approve", () => apiFetch<Report>(`${url}/approve`, { method: "POST" }), "Draft approved")}>
                {busy === "approve" ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />} Approve
              </Button>
            )}
            {!sent && report.draftStatus === "APPROVED" && !dirty && (
              <MarkSentDialog eventId={eventId} type={report.type} onSent={async (updated) => { await applied(updated); toast.success("Marked as sent — clock stopped"); }} />
            )}
            {report.draftStatus === "APPROVED" && !dirty && <ExportButtons eventId={eventId} type={report.type} />}
          </div>
        </div>
      )}

      <Dialog open={confirmAi !== null} onOpenChange={(open) => !open && setConfirmAi(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Replace the current draft?</DialogTitle>
            <DialogDescription>A new draft replaces every field and removes the approval. Your unsaved edits will be lost.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmAi(null)}>Keep current</Button>
            <Button onClick={() => generate(confirmAi === true)}>Replace draft</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
