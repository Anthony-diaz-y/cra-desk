import { ArrowRight, Check, Flame, Sparkles, Timer } from "lucide-react";
import { Deadlines } from "@/components/landing/deadlines";
import { Hero } from "@/components/landing/hero";
import { HowItWorks } from "@/components/landing/how-it-works";
import { RequestAccessForm } from "@/components/landing/request-access-form";
import { Reveal } from "@/components/landing/reveal";
import { SiteFooter } from "@/components/landing/site-footer";
import { SiteHeader } from "@/components/landing/site-header";
import { TryDemoButton } from "@/components/try-demo-button";

const INCLUDED = [
  "Upload and monitor SBOMs across every product version.",
  "Live 24-hour, 72-hour and 14-day CRA clocks.",
  "AI or template drafts, human approval and a tamper-evident audit trail.",
];

const CLOCKS = [
  { window: "24 h", label: "Early warning", state: "Sent on time", tone: "bg-success" },
  { window: "72 h", label: "Notification", state: "Clock running", tone: "bg-warning" },
  { window: "14 d", label: "Final report", state: "Not started", tone: "bg-brand-foreground/30" },
];

// Portada pública: problema del CRA, cómo funciona, demo y lista de espera.
export default function Home() {
  return (
    <>
      <SiteHeader />
      <main className="flex-1">
        <Hero />
        <Deadlines />
        <HowItWorks />
        <section className="relative overflow-hidden bg-brand text-brand-foreground">
          <div
            className="pointer-events-none absolute inset-0 opacity-[0.14] [background-image:linear-gradient(to_right,currentColor_1px,transparent_1px),linear-gradient(to_bottom,currentColor_1px,transparent_1px)] [background-size:40px_40px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)]"
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute -right-24 -top-32 size-[380px] rounded-full bg-brand-foreground/10 blur-3xl"
            aria-hidden="true"
          />
          <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 py-20 sm:px-6 md:grid-cols-[1.1fr_0.9fr] lg:px-8">
            <Reveal>
              <p className="inline-flex items-center gap-2 rounded-full border border-brand-foreground/20 bg-brand-foreground/10 px-3 py-1 text-xs font-medium">
                <Sparkles className="size-3.5" /> Live sample data
              </p>
              <h2 className="mt-5 text-3xl font-semibold tracking-[-0.025em] text-balance sm:text-[2.4rem] sm:leading-[1.1]">
                See a real case end to end.
              </h2>
              <p className="mt-4 max-w-xl text-[15px] leading-relaxed text-brand-foreground/80">
                The demo company confirmed Log4Shell on its smart door lock 30 hours ago. The early warning went out on
                time; the notification clock is still running.
              </p>
              <div className="mt-8">
                <TryDemoButton
                  label="Open the demo"
                  className="h-11 bg-brand-foreground px-5 text-brand shadow-lg shadow-black/10 hover:bg-brand-foreground/90"
                />
              </div>
            </Reveal>
            <Reveal delay={0.1}>
              <div className="rounded-3xl border border-brand-foreground/15 bg-brand-foreground/[0.07] p-5 shadow-2xl shadow-black/10 backdrop-blur-sm sm:p-6">
                <div className="flex items-center justify-between gap-3 border-b border-brand-foreground/15 pb-4">
                  <div className="flex items-center gap-2.5">
                    <span className="grid size-8 place-items-center rounded-xl bg-brand-foreground/10">
                      <Flame className="size-4" />
                    </span>
                    <div>
                      <p className="font-mono text-sm font-semibold">CVE-2021-44228</p>
                      <p className="text-[11px] text-brand-foreground/65">Smart Door Lock · 1.1.0</p>
                    </div>
                  </div>
                  <span className="inline-flex items-center gap-1 rounded-full bg-brand-foreground/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide">
                    <Timer className="size-3" /> Confirmed 30 h ago
                  </span>
                </div>
                <ul className="mt-2 divide-y divide-brand-foreground/10">
                  {CLOCKS.map((clock) => (
                    <li key={clock.window} className="flex items-center justify-between gap-4 py-3.5">
                      <div className="flex items-center gap-3">
                        <span className="font-mono text-sm font-semibold tabular">{clock.window}</span>
                        <span className="text-sm">{clock.label}</span>
                      </div>
                      <span className="inline-flex items-center gap-1.5 text-xs text-brand-foreground/75">
                        <span className={`size-1.5 rounded-full ${clock.tone}`} />
                        {clock.state}
                      </span>
                    </li>
                  ))}
                </ul>
                <div className="mt-2 flex items-center justify-between rounded-xl bg-brand-foreground/[0.07] px-3 py-2.5 text-[11px] text-brand-foreground/75">
                  <span>Read-only demo · no sign-up</span>
                  <span className="inline-flex items-center gap-1 font-medium text-brand-foreground">
                    Explore <ArrowRight className="size-3" />
                  </span>
                </div>
              </div>
            </Reveal>
          </div>
        </section>
        <section id="access" className="scroll-mt-20 border-b border-border/60 bg-muted/20">
          <div className="mx-auto grid max-w-7xl gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[1fr_460px] lg:gap-16 lg:px-8">
            <Reveal>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">Early access</p>
              <h2 className="mt-3 text-3xl font-semibold tracking-[-0.025em] sm:text-[2.4rem]">Request access</h2>
              <p className="mt-3 max-w-lg text-[15px] leading-relaxed text-muted-foreground">
                Accounts are invite-only while CRA Desk is in early access. Tell us what you build and we&apos;ll reach out.
              </p>
              <ul className="mt-8 grid gap-3.5">
                {INCLUDED.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-sm">
                    <span className="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full bg-brand/10 text-brand">
                      <Check className="size-3" strokeWidth={3} />
                    </span>
                    <span className="text-muted-foreground">{item}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-9 flex items-start gap-3 rounded-2xl border border-border/70 bg-card p-4 shadow-sm">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand/10 text-brand">
                  <Timer className="size-4" />
                </span>
                <div>
                  <p className="text-sm font-medium">No sales call required</p>
                  <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                    Start with the demo, then request an invite when you want your own workspace.
                  </p>
                </div>
              </div>
            </Reveal>
            <Reveal delay={0.1}>
              <div className="relative overflow-hidden rounded-3xl border border-border/70 bg-card p-6 shadow-soft sm:p-8">
                <div className="absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,color-mix(in_oklch,var(--brand)_60%,transparent),transparent)]" />
                <h3 className="text-lg font-medium tracking-tight">Join the early access</h3>
                <p className="mt-1 mb-6 text-sm text-muted-foreground">
                  We read every request. Expect a reply within a few working days.
                </p>
                <RequestAccessForm />
              </div>
            </Reveal>
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
