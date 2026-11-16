"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Loader2, RefreshCw, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { dateTime } from "@/lib/format";
import { CanWrite } from "@/lib/session";
import type { VersionVulnerabilities } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Estado del escaneo de vulnerabilidades de la versión, con el botón para volver a escanear.
export function ScanStatus({ productId, versionId, data }: { productId: string; versionId: string; data: VersionVulnerabilities }) {
  const client = useQueryClient();
  const running = data.status === "PENDING" || data.status === "RUNNING";
  const failed = data.status === "FAILED";

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
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border/70 bg-card px-4 py-3 shadow-sm">
      <span className="flex items-center gap-3 text-sm">
        <span className={cn("grid size-8 shrink-0 place-items-center rounded-lg", failed ? "bg-danger/10 text-danger" : running ? "bg-brand/10 text-brand" : "bg-success/10 text-success")}>
          {running ? <Loader2 className="size-4 animate-spin" /> : failed ? <TriangleAlert className="size-4" /> : <CheckCircle2 className="size-4" />}
        </span>
        <span>
          <span className="block font-medium">
            {running ? "Scanning against OSV and CISA KEV…" : failed ? `Scan failed: ${data.error ?? "unknown error"}` : `Scanned ${dateTime(data.finishedAt)}`}
          </span>
          <span className="block text-xs text-muted-foreground">
            {!running && !failed && `${data.componentsScanned ?? 0} components checked`}
            {!running && !failed && data.componentsNotScanned ? ` · ${data.componentsNotScanned} without a purl` : ""}
          </span>
        </span>
      </span>
      <CanWrite>
        <Button variant="outline" size="sm" onClick={rescan} disabled={running} className="gap-1.5 bg-background/60"><RefreshCw className="size-3.5" /> Rescan</Button>
      </CanWrite>
    </div>
  );
}
