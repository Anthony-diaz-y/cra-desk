import { BrandMark } from "@/components/brand-mark";
import { cn } from "@/lib/utils";

// Marca de CRA Desk: el escudo con reloj y check, junto al nombre.
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2.5 font-semibold tracking-tight", className)}>
      <BrandMark variant="simple" className="size-8" />
      <span className="text-[15px] leading-none">CRA Desk</span>
    </span>
  );
}
