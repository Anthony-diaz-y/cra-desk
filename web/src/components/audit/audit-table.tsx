"use client";

import { Fragment, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronDown, ScrollText } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { dateTime, short } from "@/lib/format";
import type { AuditPage } from "@/lib/types";
import { EmptyState, LoadError } from "@/components/app/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;

// Convierte el nombre de una acción, como REPORT_SENT, en texto legible.
function actionLabel(action: string): string {
  const text = action
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\bsbom\b/g, "SBOM")
    .replace(/\bapi\b/g, "API")
    .replace(/\benisa\b/g, "ENISA")
    .replace(/\bkev\b/g, "KEV")
    .replace(/\bcra\b/g, "CRA");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

// Color del badge según la acción registrada.
function actionTone(action: string): string {
  if (/(SENT|APPROVED|VERIFIED|UPLOADED)/.test(action)) return "border-success/25 bg-success/10 text-success";
  if (/(DISMISS|REVOK|FAIL|DELETE)/.test(action)) return "border-danger/25 bg-danger/10 text-danger";
  if (/(CONFIRM|CREATE|INVITE)/.test(action)) return "border-brand/20 bg-brand/10 text-brand";
  if (/(UPDATE|EDIT|SAVE)/.test(action)) return "border-warning/30 bg-warning/10 text-warning";
  return "border-border bg-muted text-muted-foreground";
}

// Tabla paginada del registro de auditoría, con los detalles desplegables de cada entrada.
export function AuditTable() {
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<number | null>(null);
  const { data, error, isLoading } = useQuery({
    queryKey: ["audit", page],
    queryFn: () => apiFetch<AuditPage>(`/api/audit?page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  });
  if (error) {
    return <LoadError error={error} />;
  }
  if (isLoading || !data) {
    return <Skeleton className="h-64 rounded-2xl" />;
  }
  if (data.total === 0) {
    return <EmptyState icon={<ScrollText className="size-5" />} title="No audit entries yet" text="Reviews, drafts, approvals, sent reports, SBOM uploads and API key changes are recorded here." />;
  }
  return (
    <div className="grid gap-3">
      <div className="overflow-hidden rounded-2xl border border-border/70 bg-card shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="w-16">#</TableHead>
                <TableHead>When</TableHead>
                <TableHead>Action</TableHead>
                <TableHead className="hidden md:table-cell">Actor</TableHead>
                <TableHead className="hidden lg:table-cell">Hash</TableHead>
                <TableHead className="w-10"><span className="sr-only">Details</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.items.map((e) => (
                <Fragment key={e.seq}>
                  <TableRow className="cursor-pointer" onClick={() => setOpen(open === e.seq ? null : e.seq)}>
                    <TableCell className="font-mono text-xs text-muted-foreground tabular">{e.seq}</TableCell>
                    <TableCell className="text-sm">{dateTime(e.occurredAt)}</TableCell>
                    <TableCell>
                      <span className={cn("rounded-full border px-2 py-0.5 text-[10px] font-medium", actionTone(e.action))}>{actionLabel(e.action)}</span>
                      <span className="ml-2 text-[11px] text-muted-foreground">{e.targetType}</span>
                    </TableCell>
                    <TableCell className="hidden text-xs md:table-cell">{e.actorId ? <>{e.actorRole} <span className="font-mono text-muted-foreground">{short(e.actorId)}</span></> : "System"}</TableCell>
                    <TableCell className="hidden font-mono text-xs text-muted-foreground lg:table-cell" title={e.hash}>{short(e.hash, 12)}</TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon-sm" aria-label={`Details of entry ${e.seq}`} aria-expanded={open === e.seq}>
                        <ChevronDown className={cn("size-4 transition-transform duration-200", open === e.seq && "rotate-180")} />
                      </Button>
                    </TableCell>
                  </TableRow>
                  {open === e.seq && (
                    <TableRow className="hover:bg-transparent">
                      <TableCell colSpan={6} className="bg-muted/25">
                        <div className="grid gap-2">
                          <pre className="max-h-72 overflow-auto rounded-xl border border-border/60 bg-background/60 p-3 whitespace-pre-wrap break-all font-mono text-[11px] leading-relaxed">{JSON.stringify(e.details, null, 2)}</pre>
                          <p className="font-mono text-[10px] leading-relaxed text-muted-foreground">prev {e.prevHash}<br />hash {e.hash}</p>
                        </div>
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
        <span className="tabular">{data.total} entries · page {data.page + 1} of {Math.max(1, data.totalPages)}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Older</Button>
        </div>
      </div>
    </div>
  );
}
