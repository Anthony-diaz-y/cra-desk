"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Boxes, CalendarClock, FileCode2, UploadCloud } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { dateTime } from "@/lib/format";
import type { Product, SbomSummary, Version, VersionVulnerabilities } from "@/lib/types";
import { LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ComponentsTable } from "./components-table";
import { SbomDropzone } from "./sbom-dropzone";
import { ScanStatus } from "./scan-status";
import { VulnerabilitiesTable } from "./vulnerabilities-table";

// Devuelve null cuando la API responde 404 (la versión todavía no tiene SBOM).
async function orNull<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return null;
    }
    throw e;
  }
}

// Tarjeta de una cifra del resumen del SBOM.
function SummaryTile({ icon, label, value, hint }: { icon: ReactNode; label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-2xl border border-border/70 bg-card p-4 shadow-sm">
      <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
        <span className="grid size-6 place-items-center rounded-lg bg-brand/[0.07] text-brand">{icon}</span>
        {label}
      </div>
      <p className="mt-3 text-2xl font-semibold leading-none tracking-tight tabular">{value}</p>
      {hint && <p className="mt-1.5 text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

// Detalle de una versión: subida del SBOM, resumen, estado del escaneo, vulnerabilidades y componentes.
export function VersionDetail({ productId, versionId }: { productId: string; versionId: string }) {
  const base = `/api/products/${productId}/versions/${versionId}`;
  const product = useQuery({ queryKey: ["products", productId], queryFn: () => apiFetch<Product>(`/api/products/${productId}`) });
  const version = useQuery({ queryKey: ["version", versionId], queryFn: () => apiFetch<Version>(base) });
  const sbom = useQuery({ queryKey: ["sbom", versionId], queryFn: () => orNull(apiFetch<SbomSummary>(`${base}/sbom`)) });
  const vulns = useQuery({
    queryKey: ["vulnerabilities", versionId],
    queryFn: () => orNull(apiFetch<VersionVulnerabilities>(`${base}/vulnerabilities`)),
    enabled: !!sbom.data,
    refetchInterval: (query) => (["PENDING", "RUNNING"].includes(query.state.data?.status ?? "") ? 5000 : false),
  });
  if (version.error) {
    return <LoadError error={version.error} />;
  }
  return (
    <>
      <PageHeader
        eyebrow={<Link href={`/app/products/${productId}`} className="inline-flex items-center gap-1 transition-colors hover:text-foreground"><ArrowLeft className="size-3.5" /> {product.data?.name ?? "Product"}</Link>}
        title={<>Version <span className="font-mono">{version.data?.version ?? "…"}</span></>}
        description="Upload the SBOM you ship. CRA Desk matches every component against OSV and flags anything in CISA's Known Exploited Vulnerabilities catalog."
      />
      <div className="grid gap-8">
        <section className="grid gap-4">
          <SbomDropzone productId={productId} versionId={versionId} />
          {sbom.data && (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <SummaryTile icon={<Boxes className="size-3.5" />} label="Components" value={sbom.data.componentCount} hint="In this SBOM" />
              <SummaryTile icon={<FileCode2 className="size-3.5" />} label="CycloneDX" value={sbom.data.specVersion} hint="Spec version" />
              <SummaryTile icon={<UploadCloud className="size-3.5" />} label="Uploaded via" value={sbom.data.uploadedVia === "CLI" ? "CLI" : "Web"} hint="Ingest channel" />
              <SummaryTile icon={<CalendarClock className="size-3.5" />} label="Uploaded" value={<span className="text-sm font-medium leading-snug">{dateTime(sbom.data.uploadedAt)}</span>} hint="Latest SBOM" />
            </div>
          )}
        </section>
        {sbom.isLoading ? <Skeleton className="h-32 rounded-2xl" /> : sbom.data ? (
          <>
            <section className="grid gap-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-medium tracking-tight">Vulnerabilities</h2>
                {vulns.data && <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground tabular">{vulns.data.items.length}</span>}
                <span className="h-px flex-1 bg-border/60" aria-hidden="true" />
              </div>
              {vulns.data ? (
                <>
                  <ScanStatus productId={productId} versionId={versionId} data={vulns.data} />
                  <VulnerabilitiesTable items={vulns.data.items} />
                </>
              ) : <Skeleton className="h-24 rounded-2xl" />}
            </section>
            <section className="grid gap-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-medium tracking-tight">Components</h2>
                <span className="h-px flex-1 bg-border/60" aria-hidden="true" />
              </div>
              <ComponentsTable productId={productId} versionId={versionId} />
            </section>
          </>
        ) : null}
      </div>
    </>
  );
}
