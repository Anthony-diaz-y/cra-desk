"use client";

import { useState } from "react";
import { Flame } from "lucide-react";
import type { Vulnerability } from "@/lib/types";
import { date } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

const COLLAPSED_EXTRA = 10;

const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "MODERATE", "LOW"];

// Ordena con las de KEV primero y después por gravedad.
function sorted(items: Vulnerability[]): Vulnerability[] {
  const rank = (v: Vulnerability) => {
    const index = SEVERITY_ORDER.indexOf((v.severity ?? "").toUpperCase());
    return index === -1 ? SEVERITY_ORDER.length : index;
  };
  return [...items].sort((a, b) => Number(b.kev) - Number(a.kev) || rank(a) - rank(b) || a.id.localeCompare(b.id));
}

// Color del badge de gravedad.
function severityTone(severity: string | null): string {
  switch (severity?.toUpperCase()) {
    case "CRITICAL":
      return "border-danger/25 bg-danger/10 text-danger";
    case "HIGH":
      return "border-warning/30 bg-warning/10 text-warning";
    case "MEDIUM":
    case "MODERATE":
      return "border-brand/20 bg-brand/10 text-brand";
    case "LOW":
      return "border-success/25 bg-success/10 text-success";
    default:
      return "border-border bg-muted text-muted-foreground";
  }
}

// Tabla de vulnerabilidades de una versión, con las explotadas activamente (KEV) primero.
export function VulnerabilitiesTable({ items }: { items: Vulnerability[] }) {
  const [showAll, setShowAll] = useState(false);
  if (items.length === 0) {
    return <p className="rounded-2xl border border-dashed border-border/80 bg-card/40 p-8 text-center text-sm text-muted-foreground">No known vulnerabilities in this SBOM.</p>;
  }
  const ordered = sorted(items);
  const limit = ordered.filter((v) => v.kev).length + COLLAPSED_EXTRA;
  const visible = showAll ? ordered : ordered.slice(0, limit);
  return (
    <div className="grid gap-3">
      <div className="overflow-hidden rounded-2xl border border-border/70 bg-card shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="px-4">Vulnerability</TableHead>
                <TableHead className="px-4">Component</TableHead>
                <TableHead className="px-4">Severity</TableHead>
                <TableHead className="px-4">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((v) => (
                <TableRow key={`${v.id}-${v.purl}`} className={v.kev ? "bg-danger/[0.035]" : undefined}>
                  <TableCell className="px-4 py-3 align-top whitespace-normal">
                    <p className="font-mono text-sm font-medium">{v.id}</p>
                    {v.aliases.length > 0 && <p className="mt-0.5 text-xs text-muted-foreground">{v.aliases.join(", ")}</p>}
                    {v.summary && <p className="mt-1.5 max-w-md text-xs leading-relaxed text-muted-foreground">{v.summary}</p>}
                  </TableCell>
                  <TableCell className="px-4 py-3 align-top font-mono text-xs">{v.componentName} {v.componentVersion}</TableCell>
                  <TableCell className="px-4 py-3 align-top">
                    <span className={cn("inline-flex rounded-full border px-2 py-0.5 text-[10px] font-medium", severityTone(v.severity))}>{v.severity ?? "—"}</span>
                  </TableCell>
                  <TableCell className="px-4 py-3 align-top">
                    {v.kev ? (
                      <span className="inline-flex items-center gap-1 rounded-full border border-danger/20 bg-danger/10 px-2 py-0.5 text-[10px] font-medium text-danger">
                        <Flame className="size-3" /> Actively exploited · KEV {date(v.kevDateAdded)}
                      </span>
                    ) : (
                      <span className="text-xs text-muted-foreground">Known</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
      {ordered.length > limit && (
        <Button variant="outline" size="sm" className="justify-self-center bg-background/60" onClick={() => setShowAll((value) => !value)}>
          {showAll ? "Show fewer" : `Show all ${ordered.length} vulnerabilities`}
        </Button>
      )}
    </div>
  );
}
