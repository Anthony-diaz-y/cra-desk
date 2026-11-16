"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowUpRight, Boxes } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { date, productClassLabel } from "@/lib/format";
import type { Product, ProductClass } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { ProductDialog } from "./product-dialog";

const CLASS_TONE: Record<ProductClass, string> = {
  DEFAULT: "border-border bg-muted text-muted-foreground",
  IMPORTANT_CLASS_I: "border-brand/20 bg-brand/10 text-brand",
  IMPORTANT_CLASS_II: "border-warning/30 bg-warning/10 text-warning",
  CRITICAL: "border-danger/25 bg-danger/10 text-danger",
};

// Lista de productos de la empresa en tarjetas, con el botón para crear uno.
export function ProductsList() {
  const { data, isLoading, error } = useQuery({ queryKey: ["products"], queryFn: () => apiFetch<Product[]>("/api/products") });
  return (
    <>
      <PageHeader title="Products" description="Everything you place on the EU market. Each version gets its own SBOM and vulnerability scan." actions={<ProductDialog />} />
      {error ? <LoadError error={error} /> : isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-44 rounded-2xl" />)}</div>
      ) : !data?.length ? (
        <EmptyState icon={<Boxes className="size-5" />} title="No products yet" text="Add your first product, create a version and upload its SBOM." action={<ProductDialog />} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((product, i) => (
            <motion.div key={product.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04, duration: 0.3 }}>
              <Link href={`/app/products/${product.id}`}
                className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-border/70 bg-card p-5 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-brand/25 hover:shadow-lift">
                <div className="pointer-events-none absolute inset-x-0 top-0 h-24 bg-[linear-gradient(180deg,color-mix(in_oklch,var(--brand)_5%,transparent),transparent)]" aria-hidden="true" />
                <div className="relative flex items-start justify-between gap-3">
                  <span className="grid size-10 place-items-center rounded-xl border border-brand/15 bg-brand/[0.07] text-brand">
                    <Boxes className="size-[18px]" />
                  </span>
                  <span className={cn("rounded-full border px-2 py-0.5 text-[10px] font-medium", CLASS_TONE[product.productClass])}>{productClassLabel(product.productClass)}</span>
                </div>
                <h2 className="relative mt-4 text-base font-medium tracking-tight">{product.name}</h2>
                {product.description && <p className="relative mt-1.5 line-clamp-2 text-sm leading-relaxed text-muted-foreground">{product.description}</p>}
                <div className="relative mt-auto flex items-center justify-between gap-3 pt-5">
                  <p className="text-[11px] text-muted-foreground">Updated {date(product.updatedAt)}</p>
                  <span className="grid size-7 place-items-center rounded-full border border-border/60 bg-background/60 text-muted-foreground transition-all duration-200 group-hover:border-brand/25 group-hover:text-brand">
                    <ArrowUpRight className="size-3.5 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                  </span>
                </div>
              </Link>
            </motion.div>
          ))}
        </div>
      )}
    </>
  );
}
