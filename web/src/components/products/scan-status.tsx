"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Loader2, RefreshCw, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { dateTime } from "@/lib/format";
import { CanWrite } from "@/lib/session";
import type { VersionVulnerabilities } from "@/lib/types";
import { Button } from "@/components/ui/button";

// Estado del escaneo de vulnerabilidades de la versión, con el botón para volver a escanear.
export function ScanStatus({ productId, versionId, data }: { productId: string; versionId: string; data: VersionVulnerabilities }) {
  const client = useQueryClient();
  const running = data.status === "PENDING" || data.status === "RUNNING";

  // Pide un nuevo escaneo a la API.
  async function rescan() {
    try {
      await apiFetch(`/api/products/${productId}/versions/${versionId}/scan`, { method: "POST" });
      toast.success("Rescan queued");
      await client.invalidateQueries({ queryKey: ["vulnerabilities", versionId] });
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Could not queue the rescan");
    }
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 bg-card px-4 py-3 text-sm">
      <span className="flex items-center gap-2">
        {running ? <Loader2 className="size-4 animate-spin text-brand" /> : data.status === "FAILED" ? <TriangleAlert className="size-4 text-danger" /> : <CheckCircle2 className="size-4 text-success" />}
        {running ? "Scanning against OSV and CISA KEV…" : data.status === "FAILED" ? `Scan failed: ${data.error ?? "unknown error"}` : `Scanned ${dateTime(data.finishedAt)} · ${data.componentsScanned ?? 0} components checked`}
        {data.componentsNotScanned ? <span className="text-muted-foreground">({data.componentsNotScanned} without a purl)</span> : null}
      </span>
      <CanWrite>
        <Button variant="outline" size="sm" onClick={rescan} disabled={running} className="gap-1.5"><RefreshCw className="size-3.5" /> Rescan</Button>
      </CanWrite>
    </div>
  );
}
