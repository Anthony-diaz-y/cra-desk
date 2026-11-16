import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "@/components/logo";

// Marco centrado de las páginas de acceso, con la marca y el fondo de cuadrícula.
export function AuthCard({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="relative flex flex-1 items-center justify-center px-4 py-16">
      <div className="bg-grid pointer-events-none absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" aria-hidden="true" />
      <div className="relative w-full max-w-sm">
        <Link href="/" className="mb-8 flex justify-center" aria-label="CRA Desk home">
          <Logo />
        </Link>
        <div className="rounded-2xl border border-border/70 bg-card p-6 shadow-xl shadow-brand/5">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 mb-6 text-sm text-muted-foreground">{subtitle}</p>
          {children}
        </div>
        {footer && <div className="mt-6 text-center text-sm text-muted-foreground">{footer}</div>}
      </div>
    </main>
  );
}
