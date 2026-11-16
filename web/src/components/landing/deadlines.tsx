import { BellRing, FileText, Send } from "lucide-react";
import { Reveal } from "./reveal";

const ITEMS = [
  {
    icon: BellRing,
    window: "24 hours",
    title: "Early warning",
    text: "As soon as you become aware that a vulnerability in your product is actively exploited.",
  },
  {
    icon: Send,
    window: "72 hours",
    title: "Vulnerability notification",
    text: "General nature of the exploit, corrective measures available and what users can do now.",
  },
  {
    icon: FileText,
    window: "14 days",
    title: "Final report",
    text: "After a fix is available: description, severity and impact, and the security update.",
  },
];

// Sección con los tres plazos de reporte del artículo 14 del CRA.
export function Deadlines() {
  return (
    <section id="deadlines" className="scroll-mt-20 border-y border-border/60 bg-muted/30">
      <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <Reveal>
          <p className="text-sm font-medium text-brand">Article 14, Regulation (EU) 2024/2847</p>
          <h2 className="mt-2 max-w-2xl text-3xl font-semibold tracking-tight text-balance">Three clocks start the moment you confirm an exploited vulnerability.</h2>
        </Reveal>
        <div className="mt-10 grid gap-4 md:grid-cols-3">
          {ITEMS.map((item, i) => (
            <Reveal key={item.title} delay={i * 0.08}>
              <div className="h-full rounded-2xl border border-border/70 bg-card p-6 transition-shadow hover:shadow-lg hover:shadow-brand/5">
                <item.icon className="size-5 text-brand" />
                <p className="mt-6 font-mono text-3xl font-semibold tabular">{item.window}</p>
                <h3 className="mt-1 font-medium">{item.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{item.text}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
