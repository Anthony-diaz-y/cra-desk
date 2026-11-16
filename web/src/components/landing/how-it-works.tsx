import { FileCheck2, ScanSearch, UploadCloud } from "lucide-react";
import { Reveal } from "./reveal";

const STEPS = [
  {
    icon: UploadCloud,
    title: "Upload your SBOM",
    text: "From the web or from CI with the cradesk CLI and GitHub Action. CycloneDX JSON, one per product version.",
  },
  {
    icon: ScanSearch,
    title: "Review KEV findings",
    text: "Components are matched against OSV. Anything in CISA's Known Exploited Vulnerabilities catalog becomes a finding for a person to confirm or dismiss.",
  },
  {
    icon: FileCheck2,
    title: "Draft, approve and send",
    text: "Get a draft from AI or a template, edit it, approve it, export it and mark it sent. Every step lands in a tamper-evident audit log.",
  },
];

// Sección "How it works" con los tres pasos del flujo.
export function HowItWorks() {
  return (
    <section id="how-it-works" className="scroll-mt-20">
      <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <Reveal>
          <h2 className="max-w-2xl text-3xl font-semibold tracking-tight text-balance">From SBOM to ENISA report, without the spreadsheet.</h2>
        </Reveal>
        <ol className="mt-10 grid gap-8 md:grid-cols-3">
          {STEPS.map((step, i) => (
            <Reveal key={step.title} delay={i * 0.08}>
              <li className="relative">
                <span className="font-mono text-sm text-muted-foreground">0{i + 1}</span>
                <div className="mt-3 grid size-11 place-items-center rounded-xl bg-brand/10 text-brand">
                  <step.icon className="size-5" />
                </div>
                <h3 className="mt-4 font-medium">{step.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{step.text}</p>
              </li>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  );
}
