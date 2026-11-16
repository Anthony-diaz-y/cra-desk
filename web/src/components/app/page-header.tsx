import type { ReactNode } from "react";
import { TriangleAlert } from "lucide-react";

// Título de página con descripción opcional y acciones a la derecha.
export function PageHeader({ title, description, actions, eyebrow }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; eyebrow?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-col gap-5 border-b border-border/60 pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <div className="mb-2 inline-flex items-center text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">{eyebrow}</div>}
        <h1 className="text-[1.75rem] font-semibold leading-tight tracking-[-0.03em] text-balance sm:text-[2rem]">{title}</h1>
        {description && <p className="mt-2 max-w-3xl text-[15px] leading-relaxed text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

// Estado vacío con ícono, título, texto y una acción opcional.
export function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text: string; action?: ReactNode }) {
  return (
    <div className="relative overflow-hidden rounded-3xl border border-dashed border-border/80 bg-card/50 px-6 py-16 text-center">
      <div className="bg-dots pointer-events-none absolute inset-0 opacity-40 [mask-image:radial-gradient(ellipse_at_center,black,transparent_68%)]" aria-hidden="true" />
      <div className="relative">
        <div className="mx-auto grid size-12 place-items-center rounded-2xl border border-brand/15 bg-brand/[0.07] text-brand shadow-sm">
          {icon}
        </div>
        <h2 className="mt-4 font-medium tracking-tight">{title}</h2>
        <p className="mx-auto mt-1.5 max-w-sm text-sm leading-relaxed text-muted-foreground">{text}</p>
        {action && <div className="mt-6">{action}</div>}
      </div>
    </div>
  );
}

// Mensaje de error de carga con el detalle que dio la API.
export function LoadError({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : "Something went wrong.";
  return (
    <p role="alert" className="flex items-start gap-2.5 rounded-2xl border border-danger/25 bg-danger/[0.07] p-4 text-sm text-danger">
      <TriangleAlert className="mt-0.5 size-4 shrink-0" />
      {message}
    </p>
  );
}
