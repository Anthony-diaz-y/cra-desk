import { FileCheck2, ShieldCheck, Timer } from "lucide-react";
import { Logo } from "@/components/logo";
import Link from "next/link";
import type { ReactNode } from "react";

const FEATURES = [
  { icon: ShieldCheck, title: "Every version covered", text: "SBOMs, OSV matching and KEV findings in one place." },
  { icon: Timer, title: "Live CRA clocks", text: "24 hours, 72 hours and 14 days — always visible." },
  { icon: FileCheck2, title: "Human-approved reports", text: "Draft with AI or a template, then review and send." },
];

// Marco centrado de las páginas de acceso, con la marca y el fondo de cuadrícula.
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <main className="relative flex flex-1 items-center overflow-hidden px-4 py-12 sm:px-6 lg:py-16">
      <div
        className="bg-grid pointer-events-none absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black,transparent_72%)]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute -left-32 top-1/4 size-[420px] rounded-full bg-brand/10 blur-3xl"
        aria-hidden="true"
      />
      <div className="relative mx-auto grid w-full max-w-6xl items-center gap-14 lg:grid-cols-[1.05fr_440px] lg:gap-20">
        <div className="hidden lg:block">
          <Link href="/" aria-label="CRA Desk home" className="inline-flex transition-opacity hover:opacity-80">
            <Logo />
          </Link>
          <div className="mt-12">
            <span className="inline-flex items-center gap-2 rounded-full border border-brand/20 bg-brand/[0.07] px-3 py-1 text-xs font-medium text-brand">
              <span className="size-1.5 rounded-full bg-brand" /> Built for the Cyber Resilience Act
            </span>
            <h2 className="mt-6 max-w-lg text-[2.6rem] font-semibold leading-[1.08] tracking-[-0.03em] text-balance">
              Every hour of the CRA clock, <span className="text-gradient-brand">under control.</span>
            </h2>
            <p className="mt-5 max-w-md text-[15px] leading-relaxed text-muted-foreground">
              Track exploited vulnerabilities, draft the three Article 14 reports and keep an audit trail your team can
              trust.
            </p>
          </div>
          <ul className="mt-10 grid max-w-md gap-5">
            {FEATURES.map((feature) => (
              <li key={feature.title} className="flex items-start gap-3.5">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-border/70 bg-card text-brand shadow-sm">
                  <feature.icon className="size-4" />
                </span>
                <div>
                  <p className="text-sm font-medium">{feature.title}</p>
                  <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{feature.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="mx-auto w-full max-w-md">
          <Link href="/" aria-label="CRA Desk home" className="mb-8 flex justify-center lg:hidden">
            <Logo />
          </Link>
          <div className="relative overflow-hidden rounded-3xl border border-border/70 bg-card p-6 shadow-lift sm:p-7">
            <div className="absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,color-mix(in_oklch,var(--brand)_55%,transparent),transparent)]" />
            <h1 className="text-2xl font-semibold tracking-[-0.02em]">{title}</h1>
            <p className="mt-1 mb-6 text-sm text-muted-foreground">{subtitle}</p>
            {children}
          </div>
          {footer && <div className="mt-6 text-center text-sm text-muted-foreground">{footer}</div>}
        </div>
      </div>
    </main>
  );
}
