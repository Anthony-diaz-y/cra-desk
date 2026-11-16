"use client";

import { Download } from "lucide-react";
import { toast } from "sonner";
import { apiDownload, saveFile } from "@/lib/api";
import type { ReportType } from "@/lib/types";
import { Button } from "@/components/ui/button";

// Botones para descargar el reporte aprobado en Markdown o JSON.
export function ExportButtons({ eventId, type }: { eventId: string; type: ReportType }) {
  // Descarga el reporte en ese formato y lo guarda en el equipo.
  async function download(format: "markdown" | "json") {
    try {
      const file = await apiDownload(`/api/events/${eventId}/reports/${type}/export?format=${format}`);
      saveFile(file.blob, file.filename);
    } catch {
      toast.error("Could not export the report");
    }
  }

  return (
    <div className="flex gap-2">
      <Button variant="outline" onClick={() => download("markdown")} className="gap-1.5"><Download className="size-4" /> Markdown</Button>
      <Button variant="outline" onClick={() => download("json")} className="gap-1.5"><Download className="size-4" /> JSON</Button>
    </div>
  );
}
