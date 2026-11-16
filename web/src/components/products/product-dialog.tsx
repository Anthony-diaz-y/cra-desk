"use client";

import { useState } from "react";
import { Pencil, Plus } from "lucide-react";
import { toast } from "sonner";
import type { Product } from "@/lib/types";
import { CanWrite } from "@/lib/session";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ProductForm } from "./product-form";

// Botón que abre el diálogo para crear un producto o editar uno existente.
export function ProductDialog({ product }: { product?: Product }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <CanWrite>
        <Button variant={product ? "outline" : "default"} onClick={() => setOpen(true)} className="h-9 gap-1.5">
          {product ? <Pencil className="size-4" /> : <Plus className="size-4" />}
          {product ? "Edit" : "New product"}
        </Button>
      </CanWrite>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{product ? "Edit product" : "New product"}</DialogTitle>
            <DialogDescription>Products are what you place on the EU market; each has its own versions and SBOMs.</DialogDescription>
          </DialogHeader>
          <ProductForm product={product} onSaved={(saved) => {
            setOpen(false);
            toast.success(product ? "Product updated" : `${saved.name} created`);
          }} />
        </DialogContent>
      </Dialog>
    </>
  );
}
