"use client";

import Link from "next/link";
import { ArrowUpRight, Flame } from "lucide-react";
import { reportTitle, dateTime } from "@/lib/format";
import type { EventResponse } from "@/lib/types";
import { cn } from "@/lib/utils";
import { DeadlineClock } from "./deadline-clock";

// Tarjeta de un evento reportable con sus tres relojes.
export function EventCard({ event }: { event: EventResponse }) {
  const allSent = event.reports.every((report) => report.sentAt !== null);
  const overdue = event.reports.some((report) => report.sentAt === null && report.status === "OVERDUE");
  const dueSoon = event.reports.some((report) => report.sentAt === null && report.status === "DUE_SOON");
  const tone = allSent ? "success" : overdue ? "danger" : dueSoon ? "warning" : "brand";
  const stateLabel = allSent ? "All reports sent" : overdue ? "Overdue report" : dueSoon ? "Due soon" : "Clocks running";
  const stateTone =
    tone === "success"
      ? "border-success/25 bg-success/10 text-success"
      : tone === "danger"
        ? "border-danger/25 bg-danger/10 text-danger"
        : tone === "warning"
          ? "border-warning/30 bg-warning/10 text-warning"
          : "border-brand/20 bg-brand/[0.07] text-brand";
  return (
    <Link href={`/app/events/${event.id}`}
      className="group relative block overflow-hidden rounded-2xl border border-border/70 bg-card p-5 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:border-brand/25 hover:shadow-lift sm:p-6">
      <span
        className={cn(
          "absolute inset-y-0 left-0 w-[3px] bg-gradient-to-b",
          tone === "success" ? "from-success/80 to-success/10" : tone === "danger" ? "from-danger/80 to-danger/10" : tone === "warning" ? "from-warning/80 to-warning/10" : "from-brand/80 to-brand/10",
        )}
        aria-hidden="true"
      />
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-danger">
              <Flame className="size-3" /> KEV
            </span>
            <p className="font-mono text-lg font-semibold tracking-tight">{event.cveId}</p>
            <span className={cn("rounded-full border px-2 py-0.5 text-[10px] font-medium", stateTone)}>{stateLabel}</span>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {event.productName} <span className="font-mono">{event.version}</span> · confirmed {dateTime(event.confirmedAt)}
          </p>
        </div>
        <span className="inline-flex items-center gap-1 rounded-full border border-border/70 px-2.5 py-1 text-xs text-muted-foreground transition-colors group-hover:border-brand/25 group-hover:text-foreground">
          Open <ArrowUpRight className="size-3.5 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
        </span>
      </div>
      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        {event.reports.map((report) => (
          <div key={report.type} className="rounded-xl border border-border/60 bg-muted/20 p-3">
            <p className="mb-2 text-xs font-medium text-muted-foreground">{reportTitle(report.type)}</p>
            <DeadlineClock dueAt={report.dueAt} startAt={event.confirmedAt} status={report.status} sentAt={report.sentAt} />
          </div>
        ))}
      </div>
    </Link>
  );
}
