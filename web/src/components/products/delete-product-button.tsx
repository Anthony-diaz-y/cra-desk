"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { CanWrite } from "@/lib/session";
import type { Product } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

// Botón para borrar un producto, con confirmación; la API lo impide si tiene versiones.
export function DeleteProductButton({ product }: { product: Product }) {
  const router = useRouter();
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Borra el producto y vuelve a la lista.
  async function remove() {
    setDeleting(true);
    setError(null);
    try {
      await apiFetch(`/api/products/${product.id}`, { method: "DELETE" });
      await client.invalidateQueries({ queryKey: ["products"] });
      toast.success(`${product.name} deleted`);
      router.push("/app/products");
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409 ? "This product has versions and can't be deleted." : "Could not delete the product.");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <CanWrite>
        <Button variant="ghost" onClick={() => setOpen(true)} className="h-9 gap-1.5 text-danger hover:text-danger">
          <Trash2 className="size-4" /> Delete
        </Button>
      </CanWrite>
      <Dialog open={open} onOpenChange={(value) => { setOpen(value); setError(null); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete {product.name}?</DialogTitle>
            <DialogDescription>This can&apos;t be undone. Products with versions can&apos;t be deleted.</DialogDescription>
          </DialogHeader>
          {error && <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</p>}
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="destructive" onClick={remove} disabled={deleting}>
              {deleting && <Loader2 className="size-4 animate-spin" />}
              Delete product
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
