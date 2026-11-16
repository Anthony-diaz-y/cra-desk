"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
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
        eyebrow={<Link href={`/app/products/${productId}`} className="inline-flex items-center gap-1 hover:text-foreground"><ArrowLeft className="size-3.5" /> {product.data?.name ?? "Product"}</Link>}
        title={<>Version <span className="font-mono">{version.data?.version ?? "…"}</span></>}
        description="Upload the SBOM you ship. CRA Desk matches every component against OSV and flags anything in CISA's Known Exploited Vulnerabilities catalog."
      />
      <div className="grid gap-8">
        <section className="grid gap-4">
          <SbomDropzone productId={productId} versionId={versionId} />
          {sbom.data && (
            <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-border/70 bg-card p-5 text-sm sm:grid-cols-4">
              <div><dt className="text-muted-foreground">Components</dt><dd className="mt-1 text-xl font-semibold tabular">{sbom.data.componentCount}</dd></div>
              <div><dt className="text-muted-foreground">CycloneDX</dt><dd className="mt-1 text-xl font-semibold">{sbom.data.specVersion}</dd></div>
              <div><dt className="text-muted-foreground">Uploaded via</dt><dd className="mt-1 text-xl font-semibold">{sbom.data.uploadedVia === "CLI" ? "CLI" : "Web"}</dd></div>
              <div><dt className="text-muted-foreground">Uploaded</dt><dd className="mt-1">{dateTime(sbom.data.uploadedAt)}</dd></div>
            </dl>
          )}
        </section>
        {sbom.isLoading ? <Skeleton className="h-32 rounded-2xl" /> : sbom.data ? (
          <>
            <section className="grid gap-4">
              <h2 className="text-lg font-medium">Vulnerabilities</h2>
              {vulns.data ? (
                <>
                  <ScanStatus productId={productId} versionId={versionId} data={vulns.data} />
                  <VulnerabilitiesTable items={vulns.data.items} />
                </>
              ) : <Skeleton className="h-24 rounded-2xl" />}
            </section>
            <section className="grid gap-4">
              <h2 className="text-lg font-medium">Components</h2>
              <ComponentsTable productId={productId} versionId={versionId} />
            </section>
          </>
        ) : null}
      </div>
    </>
  );
}
