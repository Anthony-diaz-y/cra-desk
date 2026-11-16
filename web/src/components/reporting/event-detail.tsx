"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowLeft, BellRing, CheckCircle2, FileText, Send } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { dateTime, reportTitle, reportWindow } from "@/lib/format";
import type { AiStatus, EventResponse, Report, ReportType } from "@/lib/types";
import { LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { DeadlineClock } from "./deadline-clock";
import { ReportEditor } from "./report-editor";

const TYPES: ReportType[] = ["EARLY_WARNING", "NOTIFICATION", "FINAL_REPORT"];

const ICONS: Record<ReportType, typeof BellRing> = {
  EARLY_WARNING: BellRing,
  NOTIFICATION: Send,
  FINAL_REPORT: FileText,
};

const GUIDES: Record<ReportType, string[]> = {
  EARLY_WARNING: ["Suspected malicious act", "Member states affected", "Immediate measures taken"],
  NOTIFICATION: ["Nature of the exploit", "Corrective measures", "User guidance"],
  FINAL_REPORT: ["Severity and impact", "Root cause", "Security update available"],
};

// Devuelve el estado del reporte en palabras.
function reportState(report: { sentAt: string | null; draftStatus: string } | undefined): string {
  return report?.sentAt ? "sent" : report?.draftStatus === "APPROVED" ? "approved" : report?.draftStatus === "DRAFTED" ? "draft" : "not started";
}

// Panel de un reporte: su reloj grande y el editor.
function ReportPanel({ event, type, ai }: { event: EventResponse; type: ReportType; ai: AiStatus | undefined }) {
  const { data, error } = useQuery({
    queryKey: ["report", event.id, type],
    queryFn: () => apiFetch<Report>(`/api/events/${event.id}/reports/${type}`),
  });
  const Icon = ICONS[type];
  if (error) {
    return <LoadError error={error} />;
  }
  if (!data) {
    return <Skeleton className="h-96 rounded-2xl" />;
  }
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="grid gap-5 lg:grid-cols-[286px_1fr] lg:gap-6"
    >
      <div className="h-fit rounded-2xl border border-border/70 bg-card p-5 shadow-sm lg:sticky lg:top-24">
        <div className="flex items-center gap-2">
          <span className="grid size-8 place-items-center rounded-lg bg-brand/10 text-brand">
            <Icon className="size-4" />
          </span>
          <div>
            <p className="text-sm font-medium">{reportTitle(type)}</p>
            <p className="text-[11px] text-muted-foreground">Due {reportWindow(type)} after confirmation</p>
          </div>
        </div>
        <div className="mt-5 border-t border-border/60 pt-5">
          <DeadlineClock dueAt={data.dueAt} startAt={event.confirmedAt} status={data.status} sentAt={data.sentAt} size="lg" />
        </div>
        <div className="mt-5 rounded-xl border border-border/60 bg-muted/25 p-3">
          <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">Key fields</p>
          <ul className="mt-2.5 grid gap-1.5">
            {GUIDES[type].map((item) => (
              <li key={item} className="flex items-start gap-2 text-xs text-muted-foreground">
                <span className="mt-1.5 size-1 shrink-0 rounded-full bg-brand" /> {item}
              </li>
            ))}
          </ul>
        </div>
      </div>
      <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-sm sm:p-6">
        <ReportEditor key={`${data.draftStatus}-${data.sentAt}-${JSON.stringify(data.content)}`} eventId={event.id} report={data} ai={ai} />
      </div>
    </motion.div>
  );
}

// Página de un evento reportable con una pestaña por cada reporte del art. 14.
export function EventDetail({ eventId }: { eventId: string }) {
  const [type, setType] = useState<ReportType>("EARLY_WARNING");
  const event = useQuery({ queryKey: ["event", eventId], queryFn: () => apiFetch<EventResponse>(`/api/events/${eventId}`) });
  const ai = useQuery({ queryKey: ["ai-status"], queryFn: () => apiFetch<AiStatus>("/api/reporting/ai") });
  if (event.error) {
    return <LoadError error={event.error} />;
  }
  if (!event.data) {
    return <Skeleton className="h-64 rounded-2xl" />;
  }
  const e = event.data;
  return (
    <>
      <PageHeader
        eyebrow={<Link href="/app" className="inline-flex items-center gap-1 text-muted-foreground transition-colors hover:text-foreground"><ArrowLeft className="size-3.5" /> Deadlines</Link>}
        title={<span className="font-mono">{e.cveId}</span>}
        description={<>{e.productName} <span className="font-mono">{e.version}</span> · exploitation confirmed {dateTime(e.confirmedAt)}</>}
      />
      <div role="tablist" aria-label="Reports" className="mb-6 grid gap-3 sm:grid-cols-3">
        {TYPES.map((t) => {
          const summary = e.reports.find((r) => r.type === t);
          const active = t === type;
          const Icon = ICONS[t];
          const done = Boolean(summary?.sentAt);
          return (
            <button key={t} role="tab" aria-selected={active} onClick={() => setType(t)}
              className={cn("relative overflow-hidden rounded-2xl border p-4 text-left transition-all duration-200", active ? "border-brand/40 bg-card shadow-sm" : "border-border/70 bg-card/60 hover:border-border hover:bg-card")}>
              {active && <motion.span layoutId="report-tab" className="absolute inset-0 bg-brand/[0.04]" transition={{ type: "spring", bounce: 0.15, duration: 0.35 }} />}
              <span className="relative flex items-start justify-between gap-3">
                <span className="min-w-0">
                  <span className="flex items-center gap-2 text-sm font-medium">
                    <Icon className={cn("size-3.5", active ? "text-brand" : "text-muted-foreground")} />
                    {reportTitle(t)}
                  </span>
                  <span className="mt-1 block text-[11px] text-muted-foreground">
                    {reportWindow(t)} · {reportState(summary)}
                  </span>
                </span>
                {done ? <CheckCircle2 className="size-4 shrink-0 text-success" /> : <span className={cn("mt-1 size-2 shrink-0 rounded-full", summary?.draftStatus === "APPROVED" ? "bg-brand" : "bg-border")} />}
              </span>
            </button>
          );
        })}
      </div>
      <ReportPanel key={type} event={e} type={type} ai={ai.data} />
      <p className="mt-6 text-xs text-muted-foreground">CRA Desk prepares the text and keeps the record. You submit the report on ENISA&apos;s single reporting platform. Not legal advice.</p>
    </>
  );
}
