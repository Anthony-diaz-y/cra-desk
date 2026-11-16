import { cn } from "@/lib/utils";

const SHIELD = "M32 7.5 50.5 15.6c1.6.7 2.5 2.2 2.5 3.9V31c0 11.6-8.2 20.4-21 25.6C19.2 51.4 11 42.6 11 31V19.5c0-1.7.9-3.2 2.5-3.9Z";

const TICKS = [
  [32, 15.5, 32, 19],
  [32, 44, 32, 47.5],
  [16.5, 31, 20, 31],
  [44, 31, 47.5, 31],
];

type Props = { className?: string; title?: string; variant?: "full" | "simple" };

// Marca de CRA Desk: escudo con esfera de reloj y un check; la variante simple quita las marcas del reloj para tamaños chicos.
export function BrandMark({ className, title, variant = "full" }: Props) {
  const simple = variant === "simple";
  return (
    <svg
      viewBox="0 0 64 64"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      className={cn("size-8 shrink-0", className)}
    >
      {title && <title>{title}</title>}
      <path d={SHIELD} className="stroke-brand" strokeWidth={simple ? 6 : 4.5} />
      {!simple &&
        TICKS.map(([x1, y1, x2, y2]) => (
          <line key={`${x1}-${y1}`} data-part="tick" x1={x1} y1={y1} x2={x2} y2={y2} className="stroke-brand" strokeWidth={3} />
        ))}
      <path
        data-part="check"
        d={simple ? "M22.5 31.5 29 38l12.5-13" : "M24 31.5 30 38l11.5-12.5"}
        className="stroke-success"
        strokeWidth={simple ? 6 : 4.5}
      />
    </svg>
  );
}
