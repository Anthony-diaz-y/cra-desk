"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowRight, ClipboardCheck, Timer, Activity, CircleCheck } from "lucide-react";
import { apiFetch } from "@/lib/api";
import type { EventResponse, Finding } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { EventCard } from "./event-card";

// Tarjeta de métrica del panel.
function Stat({
  icon,
  value,
  label,
  hint,
  accent = "brand",
}: {
  icon: ReactNode;
  value: number;
  label: string;
  hint: string;
  accent?: "brand" | "warning" | "danger";
}) {
  const tone =
    accent === "warning" ? "bg-warning/10 text-warning" : accent === "danger" ? "bg-danger/10 text-danger" : "bg-brand/10 text-brand";
  return (
    <div className="rounded-2xl border border-border/70 bg-card p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <span className={`grid size-9 place-items-center rounded-xl ${tone}`}>{icon}</span>
        <span className="font-mono text-3xl font-semibold leading-none tracking-tight tabular">{value}</span>
      </div>
      <p className="mt-4 text-sm font-medium">{label}</p>
      <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>
    </div>
  );
}

// Panel de plazos: hallazgos por revisar y los eventos reportables con sus relojes.
export function Overview() {
  const events = useQuery({ queryKey: ["events"], queryFn: () => apiFetch<EventResponse[]>("/api/events"), refetchInterval: 60_000 });
  const pending = useQuery({ queryKey: ["findings", "PENDING_REVIEW"], queryFn: () => apiFetch<Finding[]>("/api/findings?status=PENDING_REVIEW") });
  const count = pending.data?.length ?? 0;
  const eventList = events.data ?? [];
  const reports = eventList.flatMap((event) => event.reports);
  const openReports = reports.filter((report) => report.sentAt === null).length;
  const urgent = reports.filter((report) => report.sentAt === null && (report.status === "DUE_SOON" || report.status === "OVERDUE")).length;
  return (
    <>
      <PageHeader title="Deadlines" description="Every confirmed exploited vulnerability starts three CRA clocks. Mark each report as sent to stop its clock." />
      {count > 0 && (
        <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }}>
          <Link href="/app/findings"
            className="group mb-6 flex items-center justify-between gap-4 rounded-2xl border border-warning/35 bg-warning/[0.08] px-5 py-4 transition-colors hover:bg-warning/[0.12]">
            <span className="flex items-start gap-3">
              <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg bg-warning/15 text-warning">
                <ClipboardCheck className="size-4" />
              </span>
              <span><span className="font-medium">{count} actively exploited {count === 1 ? "vulnerability needs" : "vulnerabilities need"} review.</span>
                <span className="text-muted-foreground"> Confirm to start the clocks, or dismiss with a reason.</span></span>
            </span>
            <ArrowRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-foreground" />
          </Link>
        </motion.div>
      )}
      {events.error ? <LoadError error={events.error} /> : events.isLoading ? (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-3"><Skeleton className="h-28 rounded-2xl" /><Skeleton className="h-28 rounded-2xl" /><Skeleton className="h-28 rounded-2xl" /></div>
          <div className="grid gap-4"><Skeleton className="h-48 rounded-2xl" /><Skeleton className="h-48 rounded-2xl" /></div>
        </>
      ) : !events.data?.length ? (
        <EmptyState icon={<Timer className="size-5" />} title="No reportable events" text="When you confirm an actively exploited vulnerability, its 24-hour, 72-hour and 14-day clocks appear here." />
      ) : (
        <>
          <div className="mb-8 grid gap-4 sm:grid-cols-3">
            <Stat icon={<Timer className="size-4" />} value={eventList.length} label="Reportable events" hint="Confirmed exploited vulnerabilities" />
            <Stat icon={<Activity className="size-4" />} value={openReports} label="Open clocks" hint="Reports still to send" accent={urgent > 0 ? "warning" : "brand"} />
            <Stat icon={<ClipboardCheck className="size-4" />} value={count} label="Needs review" hint="KEV findings pending a decision" accent={count > 0 ? "danger" : "brand"} />
          </div>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h2 className="flex items-center gap-2 text-lg font-medium tracking-tight">
              Active clocks
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground tabular">{eventList.length}</span>
            </h2>
            <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
              <CircleCheck className="size-3.5 text-success" /> Live · refreshes every minute
            </span>
          </div>
          <div className="grid gap-4">
            {eventList.map((event, i) => (
              <motion.div key={event.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05, duration: 0.35 }}>
                <EventCard event={event} />
              </motion.div>
            ))}
          </div>
        </>
      )}
    </>
  );
}
