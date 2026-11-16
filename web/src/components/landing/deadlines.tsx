import { BellRing, FileText, Send } from "lucide-react";
import { Reveal } from "./reveal";

const ITEMS = [
  {
    icon: BellRing,
    window: "24 hours",
    title: "Early warning",
    text: "As soon as you become aware that a vulnerability in your product is actively exploited.",
    progress: "w-1/5",
    tone: "text-warning",
    tile: "bg-warning/10",
    bar: "from-warning/70 to-warning/20",
    caption: "Clock starts at confirmation",
  },
  {
    icon: Send,
    window: "72 hours",
    title: "Vulnerability notification",
    text: "General nature of the exploit, corrective measures available and what users can do now.",
    progress: "w-2/5",
    tone: "text-brand",
    tile: "bg-brand/10",
    bar: "from-brand/80 to-brand/20",
    caption: "Notification to ENISA",
  },
  {
    icon: FileText,
    window: "14 days",
    title: "Final report",
    text: "After a fix is available: description, severity and impact, and the security update.",
    progress: "w-full",
    tone: "text-success",
    tile: "bg-success/10",
    bar: "from-success/80 to-success/20",
    caption: "Closes the three clocks",
  },
];

// Sección con los tres plazos de reporte del artículo 14 del CRA.
export function Deadlines() {
  return (
    <section id="deadlines" className="relative scroll-mt-20 border-b border-border/60 bg-muted/25">
      <div className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
          <Reveal>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">Article 14 · Regulation (EU) 2024/2847</p>
            <h2 className="mt-3 max-w-2xl text-3xl font-semibold tracking-[-0.025em] text-balance sm:text-[2.4rem] sm:leading-[1.1]">
              Three clocks start the moment you confirm an exploited vulnerability.
            </h2>
          </Reveal>
          <Reveal delay={0.08}>
            <p className="max-w-xs border-l-2 border-brand/30 pl-5 text-sm leading-relaxed text-muted-foreground md:text-right md:border-l-0 md:border-r-2 md:pl-0 md:pr-5">
              Miss a window and the regulator can ask why. Every clock is visible, auditable and stops only when you mark
              the report as sent.
            </p>
          </Reveal>
        </div>
        <div className="mt-12 grid gap-4 md:grid-cols-3">
          {ITEMS.map((item, i) => (
            <Reveal key={item.title} delay={i * 0.08}>
              <div className="group relative h-full overflow-hidden rounded-3xl border border-border/70 bg-card p-6 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-brand/25 hover:shadow-lift">
                <div
                  className="pointer-events-none absolute -right-14 -top-14 size-40 rounded-full bg-brand/[0.04] blur-2xl transition-opacity duration-300 group-hover:opacity-100"
                  aria-hidden="true"
                />
                <div className="relative flex items-start justify-between">
                  <span className={`grid size-11 place-items-center rounded-2xl ${item.tile} ${item.tone}`}>
                    <item.icon className="size-5" />
                  </span>
                  <span className="font-mono text-xs text-muted-foreground/60">0{i + 1}</span>
                </div>
                <p className="relative mt-8 font-mono text-[2rem] font-semibold leading-none tracking-tight tabular">
                  {item.window}
                </p>
                <h3 className="relative mt-2 text-lg font-medium tracking-tight">{item.title}</h3>
                <p className="relative mt-2 text-sm leading-relaxed text-muted-foreground">{item.text}</p>
                <div className="relative mt-7">
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className={`h-full rounded-full bg-gradient-to-r ${item.bar} ${item.progress}`} />
                  </div>
                  <p className="mt-2 text-[11px] text-muted-foreground">{item.caption}</p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
