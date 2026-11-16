"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowRight, ClipboardCheck, Timer } from "lucide-react";
import { apiFetch } from "@/lib/api";
import type { EventResponse, Finding } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { EventCard } from "./event-card";

// Panel de plazos: hallazgos por revisar y los eventos reportables con sus relojes.
export function Overview() {
  const events = useQuery({ queryKey: ["events"], queryFn: () => apiFetch<EventResponse[]>("/api/events"), refetchInterval: 60_000 });
  const pending = useQuery({ queryKey: ["findings", "PENDING_REVIEW"], queryFn: () => apiFetch<Finding[]>("/api/findings?status=PENDING_REVIEW") });
  const count = pending.data?.length ?? 0;
  return (
    <>
      <PageHeader title="Deadlines" description="Every confirmed exploited vulnerability starts three CRA clocks. Mark each report as sent to stop its clock." />
      {count > 0 && (
        <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }}>
          <Link href="/app/findings"
            className="mb-6 flex items-center justify-between gap-3 rounded-2xl border border-warning/40 bg-warning/10 px-5 py-4 transition-colors hover:bg-warning/15">
            <span className="flex items-center gap-3">
              <ClipboardCheck className="size-5 text-warning" />
              <span><span className="font-medium">{count} actively exploited {count === 1 ? "vulnerability needs" : "vulnerabilities need"} review.</span>
                <span className="text-muted-foreground"> Confirm to start the clocks, or dismiss with a reason.</span></span>
            </span>
            <ArrowRight className="size-4 shrink-0" />
          </Link>
        </motion.div>
      )}
      {events.error ? <LoadError error={events.error} /> : events.isLoading ? (
        <div className="grid gap-4"><Skeleton className="h-48 rounded-2xl" /><Skeleton className="h-48 rounded-2xl" /></div>
      ) : !events.data?.length ? (
        <EmptyState icon={<Timer className="size-5" />} title="No reportable events" text="When you confirm an actively exploited vulnerability, its 24-hour, 72-hour and 14-day clocks appear here." />
      ) : (
        <div className="grid gap-4">
          {events.data.map((event, i) => (
            <motion.div key={event.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}>
              <EventCard event={event} />
            </motion.div>
          ))}
        </div>
      )}
    </>
  );
}
