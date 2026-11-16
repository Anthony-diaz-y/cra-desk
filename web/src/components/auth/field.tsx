import type { ComponentProps } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type FieldProps = ComponentProps<"input"> & { id: string; label: string; error?: string; hint?: string };

// Campo de formulario con etiqueta, ayuda opcional y el error de validación debajo.
export function Field({ id, label, error, hint, ...input }: FieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} aria-invalid={!!error} aria-describedby={describedBy} className="h-10 bg-background/60 transition-shadow" {...input} />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-danger">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
