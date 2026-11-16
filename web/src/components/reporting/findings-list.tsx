"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CheckCircle2, Flame, Loader2, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { date, dateTime } from "@/lib/format";
import { CanWrite } from "@/lib/session";
import type { EventResponse, Finding, FindingStatus } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { DismissDialog } from "./dismiss-dialog";

const TABS: { status: FindingStatus; label: string }[] = [
  { status: "PENDING_REVIEW", label: "To review" },
  { status: "CONFIRMED", label: "Confirmed" },
  { status: "DISMISSED", label: "Dismissed" },
];

// Botón que confirma el hallazgo, abre el evento reportable y lleva a él.
function ConfirmButton({ finding }: { finding: Finding }) {
  const router = useRouter();
  const client = useQueryClient();
  const [busy, setBusy] = useState(false);

  // Confirma en la API y navega al evento nuevo.
  async function confirm() {
    setBusy(true);
    try {
      const event = await apiFetch<EventResponse>(`/api/findings/${finding.id}/confirm`, { method: "POST" });
      await Promise.all([client.invalidateQueries({ queryKey: ["findings"] }), client.invalidateQueries({ queryKey: ["events"] }), client.invalidateQueries({ queryKey: ["audit"] })]);
      toast.success(`${finding.cveId} confirmed — clocks started`);
      router.push(`/app/events/${event.id}`);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Could not confirm the finding");
      setBusy(false);
    }
  }

  return (
    <CanWrite>
      <Button onClick={confirm} disabled={busy} className="gap-1.5">
        {busy ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />} Confirm
      </Button>
    </CanWrite>
  );
}

// Página de hallazgos: pestañas por estado, con confirmar o descartar los pendientes.
export function FindingsList() {
  const [status, setStatus] = useState<FindingStatus>("PENDING_REVIEW");
  const { data, isLoading, error } = useQuery({
    queryKey: ["findings", status],
    queryFn: () => apiFetch<Finding[]>(`/api/findings?status=${status}`),
  });
  return (
    <>
      <PageHeader title="Findings" description="Vulnerabilities in your SBOMs that are listed in CISA's Known Exploited Vulnerabilities catalog. A person decides whether each one affects your product." />
      <div role="tablist" aria-label="Finding status" className="mb-6 inline-flex rounded-xl border border-border/70 bg-muted/40 p-1">
        {TABS.map((tab) => (
          <button key={tab.status} role="tab" aria-selected={status === tab.status} onClick={() => setStatus(tab.status)}
            className={cn("relative rounded-lg px-4 py-1.5 text-sm transition-colors", status === tab.status ? "text-foreground" : "text-muted-foreground hover:text-foreground")}>
            {status === tab.status && <motion.span layoutId="findings-tab" className="absolute inset-0 rounded-lg bg-background shadow-sm" transition={{ type: "spring", bounce: 0.15, duration: 0.35 }} />}
            <span className="relative">{tab.label}</span>
          </button>
        ))}
      </div>
      {error ? <LoadError error={error} /> : isLoading ? <Skeleton className="h-40 rounded-2xl" /> : !data?.length ? (
        <EmptyState icon={<ShieldCheck className="size-5" />} title={status === "PENDING_REVIEW" ? "Nothing to review" : "Nothing here yet"}
          text={status === "PENDING_REVIEW" ? "No actively exploited vulnerabilities are waiting for a decision." : "Findings you review will show up here."} />
      ) : (
        <ul className="grid gap-3">
          {data.map((f, i) => (
            <motion.li key={f.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}
              className="flex flex-col gap-4 rounded-2xl border border-border/70 bg-card p-5 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono font-semibold">{f.cveId}</span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-danger/15 px-2 py-0.5 text-xs font-medium text-danger"><Flame className="size-3" /> KEV {date(f.kevDateAdded)}</span>
                  {f.severity && <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{f.severity}</span>}
                  {!f.present && <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">No longer in the latest scan</span>}
                </div>
                <p className="mt-1 text-sm text-muted-foreground">{f.productName} <span className="font-mono">{f.version}</span> · {f.vulnerabilityId} · found {dateTime(f.createdAt)}</p>
                {f.summary && <p className="mt-2 text-sm">{f.summary}</p>}
              </div>
              {status === "PENDING_REVIEW" && (
                <div className="flex shrink-0 gap-2">
                  <DismissDialog findingId={f.id} cveId={f.cveId} />
                  <ConfirmButton finding={f} />
                </div>
              )}
            </motion.li>
          ))}
        </ul>
      )}
    </>
  );
}
