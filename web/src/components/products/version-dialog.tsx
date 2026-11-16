"use client";

import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2, Plus } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { CanWrite } from "@/lib/session";
import type { Version } from "@/lib/types";
import { Field } from "@/components/auth/field";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";

// Botón y diálogo para crear una versión de un producto (las versiones no se editan).
export function VersionDialog({ productId }: { productId: string }) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [version, setVersion] = useState("");
  const [releasedAt, setReleasedAt] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  // Crea la versión en la API y refresca la lista.
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      const saved = await apiFetch<Version>(`/api/products/${productId}/versions`, {
        method: "POST",
        json: { version: version.trim(), releasedAt: releasedAt || null },
      });
      await client.invalidateQueries({ queryKey: ["versions", productId] });
      toast.success(`Version ${saved.version} created`);
      setOpen(false);
      setVersion("");
      setReleasedAt("");
    } catch (e) {
      setErrors(e instanceof ApiError ? (Object.keys(e.fieldErrors).length ? e.fieldErrors : { version: e.message }) : { form: "Could not create the version." });
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <CanWrite>
        <Button onClick={() => setOpen(true)} className="h-9 gap-1.5"><Plus className="size-4" /> New version</Button>
      </CanWrite>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>New version</DialogTitle>
            <DialogDescription>A firmware or software release. Upload its SBOM next.</DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} noValidate className="grid gap-4">
            <Field id="version-name" label="Version" placeholder="1.2.0" required maxLength={64} value={version} onChange={(e) => setVersion(e.target.value)} error={errors.version} />
            <Field id="version-date" label="Release date (optional)" type="date" value={releasedAt} onChange={(e) => setReleasedAt(e.target.value)} error={errors.releasedAt} />
            {errors.form && <p role="alert" className="text-sm text-danger">{errors.form}</p>}
            <Button type="submit" disabled={saving} className="h-10">
              {saving && <Loader2 className="size-4 animate-spin" />}
              Create version
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
