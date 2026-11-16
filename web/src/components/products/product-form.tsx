"use client";

import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { ApiError, apiFetch } from "@/lib/api";
import { productClassLabel } from "@/lib/format";
import type { Product, ProductClass } from "@/lib/types";
import { Field } from "@/components/auth/field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

const CLASSES: ProductClass[] = ["DEFAULT", "IMPORTANT_CLASS_I", "IMPORTANT_CLASS_II", "CRITICAL"];

// Traduce el error de la API a un mensaje claro, en especial el de edición simultánea.
function conflictMessage(e: ApiError): string {
  return /modified by someone else/i.test(e.message) ? "Someone else changed this product. Reload and try again." : e.message;
}

// Formulario para crear o editar un producto, con control de versión de fila al editar.
export function ProductForm({ product, onSaved }: { product?: Product; onSaved: (saved: Product) => void }) {
  const client = useQueryClient();
  const [name, setName] = useState(product?.name ?? "");
  const [description, setDescription] = useState(product?.description ?? "");
  const [productClass, setProductClass] = useState<ProductClass>(product?.productClass ?? "DEFAULT");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  // Guarda el producto en la API y refresca las listas.
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setErrors({});
    const body = { name: name.trim(), description: description.trim() || null, productClass, rowVersion: product?.rowVersion };
    try {
      const saved = product
        ? await apiFetch<Product>(`/api/products/${product.id}`, { method: "PUT", json: body })
        : await apiFetch<Product>("/api/products", { method: "POST", json: body });
      await client.invalidateQueries({ queryKey: ["products"] });
      onSaved(saved);
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(Object.keys(e.fieldErrors).length ? e.fieldErrors : { form: e.status === 409 ? conflictMessage(e) : e.message });
      } else {
        setErrors({ form: "Could not save the product." });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <Field id="product-name" label="Name" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} error={errors.name} />
      <div className="grid gap-1.5">
        <Label htmlFor="product-class">CRA product class</Label>
        <select id="product-class" value={productClass} onChange={(e) => setProductClass(e.target.value as ProductClass)}
          className="h-10 rounded-lg border border-input bg-transparent px-3 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
          {CLASSES.map((c) => <option key={c} value={c}>{productClassLabel(c)}</option>)}
        </select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="product-description">Description <span className="text-muted-foreground">(optional)</span></Label>
        <Textarea id="product-description" rows={3} maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        {errors.description && <p className="text-sm text-danger">{errors.description}</p>}
      </div>
      {errors.form && <p role="alert" className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{errors.form}</p>}
      <Button type="submit" disabled={saving} className="h-10">
        {saving && <Loader2 className="size-4 animate-spin" />}
        {product ? "Save changes" : "Create product"}
      </Button>
    </form>
  );
}
