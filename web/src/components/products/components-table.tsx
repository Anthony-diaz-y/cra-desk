"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { ComponentPage } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const PAGE_SIZE = 20;

// Tabla paginada de los componentes del SBOM, ordenados por nombre y versión.
export function ComponentsTable({ productId, versionId }: { productId: string; versionId: string }) {
  const [page, setPage] = useState(0);
  const { data } = useQuery({
    queryKey: ["components", versionId, page],
    queryFn: () => apiFetch<ComponentPage>(`/api/products/${productId}/versions/${versionId}/sbom/components?page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  });
  if (!data) {
    return null;
  }
  return (
    <div className="grid gap-3">
      <div className="overflow-hidden rounded-2xl border border-border/70 bg-card shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Name</TableHead>
                <TableHead>Version</TableHead>
                <TableHead className="hidden md:table-cell">Package URL</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.items.map((c, i) => (
                <TableRow key={`${c.bomRef ?? c.purl ?? c.name}-${i}`}>
                  <TableCell className="whitespace-normal text-sm">{c.group ? <span className="text-muted-foreground">{c.group}/</span> : null}{c.name}</TableCell>
                  <TableCell className="font-mono text-xs">{c.version ?? "—"}</TableCell>
                  <TableCell className="hidden max-w-sm truncate font-mono text-xs text-muted-foreground md:table-cell">{c.purl ?? <span className="text-muted-foreground/60">no purl</span>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
        <span className="tabular">{data.totalItems} components · page {data.page + 1} of {Math.max(1, data.totalPages)}</span>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
        </div>
      </div>
    </div>
  );
}
