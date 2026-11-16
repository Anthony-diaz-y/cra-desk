import { FileCheck2, ScanSearch, UploadCloud } from "lucide-react";
import { Reveal } from "./reveal";

const STEPS = [
  {
    icon: UploadCloud,
    title: "Upload your SBOM",
    text: "From the web or from CI with the cradesk CLI and GitHub Action. CycloneDX JSON, one per product version.",
    meta: "Web · CLI · GitHub Action",
  },
  {
    icon: ScanSearch,
    title: "Review KEV findings",
    text: "Components are matched against OSV. Anything in CISA's Known Exploited Vulnerabilities catalog becomes a finding for a person to confirm or dismiss.",
    meta: "OSV + CISA KEV · human decision",
  },
  {
    icon: FileCheck2,
    title: "Draft, approve and send",
    text: "Get a draft from AI or a template, edit it, approve it, export it and mark it sent. Every step lands in a tamper-evident audit log.",
    meta: "AI or template · full audit trail",
  },
];

// Sección "How it works" con los tres pasos del flujo.
export function HowItWorks() {
  return (
    <section id="how-it-works" className="relative scroll-mt-20 overflow-hidden">
      <div className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <Reveal>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">How it works</p>
          <div className="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <h2 className="max-w-2xl text-3xl font-semibold tracking-[-0.025em] text-balance sm:text-[2.4rem] sm:leading-[1.1]">
              From SBOM to ENISA report, without the spreadsheet.
            </h2>
            <p className="max-w-sm text-sm leading-relaxed text-muted-foreground">
              A focused workflow for small manufacturers: ingest, review, report. No compliance platform to learn from
              scratch.
            </p>
          </div>
        </Reveal>
        <ol className="relative mt-14 grid gap-10 md:grid-cols-3 md:gap-8">
          <div
            className="pointer-events-none absolute left-[2.25rem] right-[2.25rem] top-6 hidden h-px bg-[linear-gradient(90deg,var(--border),color-mix(in_oklch,var(--brand)_45%,transparent),var(--border))] md:block"
            aria-hidden="true"
          />
          {STEPS.map((step, i) => (
            <Reveal key={step.title} delay={i * 0.09}>
              <li className="group relative">
                <div className="flex items-center gap-4">
                  <span className="relative z-10 grid size-12 place-items-center rounded-2xl border border-border/70 bg-card text-brand shadow-sm transition-all duration-300 group-hover:border-brand/30 group-hover:shadow-md">
                    <step.icon className="size-5" />
                  </span>
                  <span className="font-mono text-sm text-muted-foreground">0{i + 1}</span>
                </div>
                <h3 className="mt-6 text-lg font-medium tracking-tight">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{step.text}</p>
                <p className="mt-5 inline-flex rounded-full border border-border/70 bg-muted/40 px-2.5 py-1 text-[11px] font-medium text-muted-foreground">
                  {step.meta}
                </p>
              </li>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  );
}
