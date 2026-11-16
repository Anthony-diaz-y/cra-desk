"use client";

import { useRef, useState, type DragEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { FileJson, Loader2, UploadCloud } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import { cn } from "@/lib/utils";

const MAX_BYTES = 5 * 1024 * 1024;

type UploadError = { message: string; fields: Record<string, string> };

// Zona para arrastrar o elegir un SBOM CycloneDX JSON y subirlo a la versión.
export function SbomDropzone({ productId, versionId }: { productId: string; versionId: string }) {
  const client = useQueryClient();
  const { isDemo: demo, isLoading: sessionLoading } = useSession();
  const isDemo = demo || sessionLoading;
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<UploadError | null>(null);

  // Comprueba el tamaño y sube el archivo tal cual, como CycloneDX JSON.
  async function upload(file: File) {
    setError(null);
    if (file.size > MAX_BYTES) {
      setError({ message: "This file is larger than 5 MB.", fields: {} });
      return;
    }
    setUploading(true);
    try {
      await apiFetch(`/api/products/${productId}/versions/${versionId}/sbom`, {
        method: "PUT",
        body: file,
        headers: { "Content-Type": "application/vnd.cyclonedx+json" },
      });
      await Promise.all([
        client.invalidateQueries({ queryKey: ["sbom", versionId] }),
        client.invalidateQueries({ queryKey: ["components", versionId] }),
        client.invalidateQueries({ queryKey: ["vulnerabilities", versionId] }),
        client.invalidateQueries({ queryKey: ["audit"] }),
      ]);
      toast.success("SBOM uploaded — scanning for vulnerabilities");
    } catch (e) {
      setError(e instanceof ApiError ? { message: e.message, fields: e.fieldErrors } : { message: "Upload failed.", fields: {} });
    } finally {
      setUploading(false);
      if (input.current) {
        input.current.value = "";
      }
    }
  }

  // Sube el archivo soltado sobre la zona.
  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file && !isDemo) {
      void upload(file);
    }
  }

  return (
    <div>
      <label
        htmlFor={`sbom-${versionId}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "group relative flex cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-all duration-200",
          dragging ? "border-brand bg-brand/[0.06] shadow-sm" : "border-border/80 bg-card/40 hover:border-brand/45 hover:bg-brand/[0.02]",
          isDemo && "pointer-events-none opacity-60",
        )}
      >
        <div className="pointer-events-none absolute inset-0 bg-dots opacity-25 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" aria-hidden="true" />
        <div className="relative">
          <span className={cn("mx-auto grid size-12 place-items-center rounded-2xl border transition-colors", dragging ? "border-brand/30 bg-brand/10 text-brand" : "border-border/70 bg-background/70 text-brand shadow-sm")}>
            {uploading ? <Loader2 className="size-5 animate-spin" /> : <UploadCloud className="size-5" />}
          </span>
          <p className="mt-4 font-medium">{uploading ? "Uploading…" : "Drop a CycloneDX JSON SBOM here"}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {isDemo ? "Uploads are disabled in the demo." : <>or <span className="font-medium text-foreground underline decoration-brand/40 underline-offset-4">browse your files</span></>}
          </p>
          <p className="mt-3 text-xs text-muted-foreground">Up to 5 MB. Replaces the current SBOM of this version.</p>
          <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5">
            {["CycloneDX 1.4", "1.5", "1.6"].map((tag) => (
              <span key={tag} className="rounded-full border border-border/60 bg-background/60 px-2 py-0.5 text-[10px] text-muted-foreground">{tag}</span>
            ))}
          </div>
        </div>
        <input id={`sbom-${versionId}`} ref={input} type="file" accept=".json,application/json,application/vnd.cyclonedx+json"
          aria-label="SBOM file" className="sr-only" disabled={isDemo || uploading}
          onChange={(e) => { const file = e.target.files?.[0]; if (file) { void upload(file); } }} />
      </label>
      {error && (
        <div role="alert" className="mt-3 rounded-xl border border-danger/25 bg-danger/[0.07] px-3.5 py-2.5 text-sm text-danger">
          <p className="flex items-center gap-2 font-medium"><FileJson className="size-4" />{error.message}</p>
          {Object.keys(error.fields).length > 0 && (
            <ul className="mt-1 list-inside list-disc">
              {Object.entries(error.fields).map(([field, message]) => (
                <li key={field}><code className="font-mono">{field}</code>: {message}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
