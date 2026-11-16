import { ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";

// Marca de CRA Desk: escudo con el nombre.
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-semibold tracking-tight", className)}>
      <span className="grid size-7 place-items-center rounded-lg bg-brand text-brand-foreground">
        <ShieldCheck className="size-4" />
      </span>
      CRA Desk
    </span>
  );
}
