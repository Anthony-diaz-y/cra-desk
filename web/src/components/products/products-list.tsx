"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Boxes, ChevronRight } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { date, productClassLabel } from "@/lib/format";
import type { Product } from "@/lib/types";
import { EmptyState, LoadError, PageHeader } from "@/components/app/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ProductDialog } from "./product-dialog";

// Lista de productos de la empresa en tarjetas, con el botón para crear uno.
export function ProductsList() {
  const { data, isLoading, error } = useQuery({ queryKey: ["products"], queryFn: () => apiFetch<Product[]>("/api/products") });
  return (
    <>
      <PageHeader title="Products" description="Everything you place on the EU market. Each version gets its own SBOM and vulnerability scan." actions={<ProductDialog />} />
      {error ? <LoadError error={error} /> : isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-36 rounded-2xl" />)}</div>
      ) : !data?.length ? (
        <EmptyState icon={<Boxes className="size-5" />} title="No products yet" text="Add your first product, create a version and upload its SBOM." action={<ProductDialog />} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((product, i) => (
            <motion.div key={product.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
              <Link href={`/app/products/${product.id}`}
                className="group flex h-full flex-col rounded-2xl border border-border/70 bg-card p-5 transition-all hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-lg hover:shadow-brand/5">
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-medium">{product.name}</h2>
                  <ChevronRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                </div>
                <span className="mt-2 w-fit rounded-full bg-brand/10 px-2 py-0.5 text-xs font-medium text-brand">{productClassLabel(product.productClass)}</span>
                {product.description && <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">{product.description}</p>}
                <p className="mt-auto pt-4 text-xs text-muted-foreground">Updated {date(product.updatedAt)}</p>
              </Link>
            </motion.div>
          ))}
        </div>
      )}
    </>
  );
}
