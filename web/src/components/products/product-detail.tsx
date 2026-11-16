"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ChevronRight, GitBranch } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { date, productClassLabel } from "@/lib/format";
import type { Product, Version } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { DeleteProductButton } from "./delete-product-button";
import { ProductDialog } from "./product-dialog";
import { VersionDialog } from "./version-dialog";

// Detalle de un producto con su lista de versiones.
export function ProductDetail({ productId }: { productId: string }) {
  const product = useQuery({ queryKey: ["products", productId], queryFn: () => apiFetch<Product>(`/api/products/${productId}`) });
  const versions = useQuery({ queryKey: ["versions", productId], queryFn: () => apiFetch<Version[]>(`/api/products/${productId}/versions`) });
  if (product.error) {
    return <LoadError error={product.error} />;
  }
  if (!product.data) {
    return <Skeleton className="h-40 rounded-2xl" />;
  }
  const p = product.data;
  return (
    <>
      <PageHeader
        eyebrow={<Link href="/app/products" className="inline-flex items-center gap-1 transition-colors hover:text-foreground"><ArrowLeft className="size-3.5" /> Products</Link>}
        title={p.name}
        description={p.description ?? undefined}
        actions={<><span className="rounded-full border border-brand/20 bg-brand/10 px-2.5 py-1 text-xs font-medium text-brand">{productClassLabel(p.productClass)}</span><ProductDialog product={p} /><DeleteProductButton product={p} /></>}
      />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-medium tracking-tight">
          Versions
          {versions.data && <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground tabular">{versions.data.length}</span>}
        </h2>
        <VersionDialog productId={productId} />
      </div>
      {versions.error ? <LoadError error={versions.error} /> : !versions.data ? <Skeleton className="h-24 rounded-2xl" /> : versions.data.length === 0 ? (
        <EmptyState icon={<GitBranch className="size-5" />} title="No versions yet" text="Create the version you ship, then upload its SBOM to start monitoring it." action={<VersionDialog productId={productId} />} />
      ) : (
        <ul className="divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/70 bg-card shadow-sm">
          {versions.data.map((v) => (
            <li key={v.id}>
              <Link href={`/app/products/${productId}/versions/${v.id}`} className="group flex items-center gap-4 px-5 py-4 transition-colors hover:bg-muted/30">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-border/60 bg-muted/40 text-muted-foreground transition-colors group-hover:border-brand/20 group-hover:bg-brand/[0.06] group-hover:text-brand">
                  <GitBranch className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-mono font-medium">{v.version}</p>
                  <p className="text-xs text-muted-foreground">Released {date(v.releasedAt)}</p>
                </div>
                <span className="hidden text-xs text-muted-foreground transition-colors group-hover:text-foreground sm:inline">View SBOM</span>
                <ChevronRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-foreground" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
