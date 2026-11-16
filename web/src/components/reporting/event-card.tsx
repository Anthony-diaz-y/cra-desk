"use client";

import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { reportTitle, dateTime } from "@/lib/format";
import type { EventResponse } from "@/lib/types";
import { DeadlineClock } from "./deadline-clock";

// Tarjeta de un evento reportable con sus tres relojes.
export function EventCard({ event }: { event: EventResponse }) {
  return (
    <Link href={`/app/events/${event.id}`}
      className="group block rounded-2xl border border-border/70 bg-card p-5 transition-all hover:border-brand/40 hover:shadow-lg hover:shadow-brand/5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-mono text-lg font-semibold">{event.cveId}</p>
          <p className="text-sm text-muted-foreground">{event.productName} <span className="font-mono">{event.version}</span> · confirmed {dateTime(event.confirmedAt)}</p>
        </div>
        <span className="inline-flex items-center gap-1 text-sm text-muted-foreground group-hover:text-foreground">Open <ArrowUpRight className="size-4" /></span>
      </div>
      <div className="mt-5 grid gap-5 sm:grid-cols-3">
        {event.reports.map((r) => (
          <div key={r.type}>
            <p className="mb-2 text-sm font-medium">{reportTitle(r.type)}</p>
            <DeadlineClock dueAt={r.dueAt} startAt={event.confirmedAt} status={r.status} sentAt={r.sentAt} />
          </div>
        ))}
      </div>
    </Link>
  );
}
