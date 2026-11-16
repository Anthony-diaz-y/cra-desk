"use client";

import { useState } from "react";
import { Flame } from "lucide-react";
import type { Vulnerability } from "@/lib/types";
import { date } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

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

// Tabla de vulnerabilidades de una versión, con las explotadas activamente (KEV) primero.
export function VulnerabilitiesTable({ items }: { items: Vulnerability[] }) {
  const [showAll, setShowAll] = useState(false);
  if (items.length === 0) {
    return <p className="rounded-xl border border-dashed border-border p-6 text-center text-sm text-muted-foreground">No known vulnerabilities in this SBOM.</p>;
  }
  const ordered = sorted(items);
  const limit = ordered.filter((v) => v.kev).length + COLLAPSED_EXTRA;
  const visible = showAll ? ordered : ordered.slice(0, limit);
  return (
    <div className="grid gap-3">
    <div className="overflow-x-auto rounded-xl border border-border/70">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Vulnerability</TableHead>
            <TableHead>Component</TableHead>
            <TableHead>Severity</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {visible.map((v) => (
            <TableRow key={`${v.id}-${v.purl}`} className={v.kev ? "bg-danger/5" : undefined}>
              <TableCell className="align-top">
                <p className="font-mono text-sm">{v.id}</p>
                {v.aliases.length > 0 && <p className="text-xs text-muted-foreground">{v.aliases.join(", ")}</p>}
                {v.summary && <p className="mt-1 max-w-md text-sm text-muted-foreground">{v.summary}</p>}
              </TableCell>
              <TableCell className="align-top font-mono text-xs">{v.componentName} {v.componentVersion}</TableCell>
              <TableCell className="align-top text-sm">{v.severity ?? "—"}</TableCell>
              <TableCell className="align-top">
                {v.kev ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-danger/15 px-2 py-0.5 text-xs font-medium text-danger">
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
    {ordered.length > limit && (
      <Button variant="outline" size="sm" className="justify-self-center" onClick={() => setShowAll((value) => !value)}>
        {showAll ? "Show fewer" : `Show all ${ordered.length} vulnerabilities`}
      </Button>
    )}
    </div>
  );
}
