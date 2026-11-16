"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowLeft } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { dateTime, reportTitle, reportWindow } from "@/lib/format";
import type { AiStatus, EventResponse, Report, ReportType } from "@/lib/types";
import { LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { DeadlineClock } from "./deadline-clock";
import { ReportEditor } from "./report-editor";

const TYPES: ReportType[] = ["EARLY_WARNING", "NOTIFICATION", "FINAL_REPORT"];

// Panel de un reporte: su reloj grande y el editor.
function ReportPanel({ event, type, ai }: { event: EventResponse; type: ReportType; ai: AiStatus | undefined }) {
  const { data, error } = useQuery({
    queryKey: ["report", event.id, type],
    queryFn: () => apiFetch<Report>(`/api/events/${event.id}/reports/${type}`),
  });
  if (error) {
    return <LoadError error={error} />;
  }
  if (!data) {
    return <Skeleton className="h-96 rounded-2xl" />;
  }
  return (
    <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
      <div className="h-fit rounded-2xl border border-border/70 bg-card p-5 lg:sticky lg:top-20">
        <p className="text-sm font-medium">{reportTitle(type)}</p>
        <p className="mb-4 text-xs text-muted-foreground">Due {reportWindow(type)} after confirmation</p>
        <DeadlineClock dueAt={data.dueAt} startAt={event.confirmedAt} status={data.status} sentAt={data.sentAt} size="lg" />
      </div>
      <div className="rounded-2xl border border-border/70 bg-card p-5">
        <ReportEditor key={`${data.draftStatus}-${data.sentAt}-${JSON.stringify(data.content)}`} eventId={event.id} report={data} ai={ai} />
      </div>
    </div>
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
        eyebrow={<Link href="/app" className="inline-flex items-center gap-1 hover:text-foreground"><ArrowLeft className="size-3.5" /> Deadlines</Link>}
        title={<span className="font-mono">{e.cveId}</span>}
        description={<>{e.productName} <span className="font-mono">{e.version}</span> · exploitation confirmed {dateTime(e.confirmedAt)}</>}
      />
      <div role="tablist" aria-label="Reports" className="mb-6 grid gap-2 sm:grid-cols-3">
        {TYPES.map((t) => {
          const summary = e.reports.find((r) => r.type === t);
          const active = t === type;
          return (
            <button key={t} role="tab" aria-selected={active} onClick={() => setType(t)}
              className={cn("relative rounded-xl border px-4 py-3 text-left transition-colors", active ? "border-brand/50" : "border-border/70 hover:bg-muted/40")}>
              {active && <motion.span layoutId="report-tab" className="absolute inset-0 rounded-xl bg-brand/5" transition={{ type: "spring", bounce: 0.15, duration: 0.35 }} />}
              <span className="relative block text-sm font-medium">{reportTitle(t)}</span>
              <span className="relative block text-xs text-muted-foreground">
                {reportWindow(t)} · {summary?.sentAt ? "sent" : summary?.draftStatus === "APPROVED" ? "approved" : summary?.draftStatus === "DRAFTED" ? "draft" : "not started"}
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
