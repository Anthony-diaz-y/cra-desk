"use client";

import { motion } from "motion/react";
import { ArrowRight, Check, Flame, ShieldCheck, Sparkles } from "lucide-react";
import { TryDemoButton } from "@/components/try-demo-button";
import { CountdownDemo } from "./countdown-demo";

const TRUST = ["CycloneDX SBOMs", "OSV matching", "CISA KEV"];

const REPORTS = [
  { window: "24 h", label: "Early warning", state: "On track", tone: "text-brand", dot: "bg-brand" },
  { window: "72 h", label: "Notification", state: "Draft due", tone: "text-warning", dot: "bg-warning" },
  { window: "14 d", label: "Final report", state: "Not started", tone: "text-muted-foreground", dot: "bg-border" },
];

// Portada principal: el mensaje, los botones de demo y acceso, y el reloj de 24 horas.
export function Hero() {
  return (
    <section className="relative overflow-hidden border-b border-border/60">
      <div
        className="bg-grid pointer-events-none absolute inset-0 [mask-image:radial-gradient(ellipse_at_top,black,transparent_72%)]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute -top-56 left-1/2 h-[560px] w-[860px] -translate-x-1/2 rounded-full bg-brand/12 blur-3xl"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute -right-40 top-24 size-[420px] rounded-full bg-[oklch(0.75_0.12_165)]/10 blur-3xl"
        aria-hidden="true"
      />
      <div className="relative mx-auto grid max-w-7xl items-center gap-14 px-4 py-20 sm:px-6 md:gap-16 md:py-24 lg:grid-cols-[1.04fr_0.96fr] lg:px-8 lg:py-28">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="inline-flex items-center gap-2 rounded-full border border-brand/25 bg-brand/[0.07] px-3 py-1.5 text-xs font-medium text-brand shadow-sm shadow-brand/5">
            <span className="relative flex size-1.5">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand/60 motion-reduce:animate-none" />
              <span className="relative inline-flex size-1.5 rounded-full bg-brand" />
            </span>
            EU Cyber Resilience Act · reporting applies from 11 September 2026
          </span>
          <h1 className="mt-7 text-[2.55rem] font-semibold leading-[1.04] tracking-[-0.035em] text-balance sm:text-6xl lg:text-[4.1rem]">
            Report actively exploited vulnerabilities to ENISA{" "}
            <span className="text-gradient-brand">on time.</span>
          </h1>
          <p className="mt-6 max-w-xl text-[1.075rem] leading-relaxed text-pretty text-muted-foreground">
            CRA Desk watches the SBOMs of your connected products, flags vulnerabilities that attackers are already
            exploiting, and helps you draft the 24-hour, 72-hour and 14-day reports — with a person approving every word.
          </p>
          <div className="mt-9 flex flex-col items-stretch gap-3 sm:flex-row sm:flex-wrap sm:items-center">
            <TryDemoButton className="w-full justify-center sm:w-auto" />
            <a
              href="#access"
              className="group inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-border/80 bg-card px-5 text-base font-medium shadow-sm transition-all hover:-translate-y-0.5 hover:border-brand/30 hover:bg-muted/50 hover:shadow-md sm:w-auto"
            >
              Request access
              <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-foreground" />
            </a>
          </div>
          <div className="mt-7 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5 font-medium text-foreground/80">
              <ShieldCheck className="size-3.5 text-brand" /> Built for CRA Article 14
            </span>
            <span className="hidden h-3 w-px bg-border sm:block" aria-hidden="true" />
            {TRUST.map((item) => (
              <span key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-3 text-brand" /> {item}
              </span>
            ))}
          </div>
          <p className="mt-3 text-sm text-muted-foreground">No sign-up needed for the demo. Read-only, with sample data.</p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 18 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.12, ease: [0.22, 1, 0.36, 1] }}
          className="relative mx-auto w-full max-w-[480px] lg:max-w-none"
        >
          <div
            className="absolute -inset-3 rounded-[2rem] border border-brand/10 bg-brand/[0.025]"
            aria-hidden="true"
          />
          <div className="relative overflow-hidden rounded-3xl border border-border/70 bg-card/85 shadow-lift backdrop-blur">
            <div
              className="pointer-events-none absolute inset-x-0 top-0 h-32 bg-[linear-gradient(180deg,color-mix(in_oklch,var(--brand)_9%,transparent),transparent)]"
              aria-hidden="true"
            />
            <div className="relative flex items-center justify-between gap-3 border-b border-border/60 px-5 py-3.5">
              <div className="flex items-center gap-2.5">
                <span className="grid size-7 place-items-center rounded-lg bg-danger/10 text-danger">
                  <Flame className="size-3.5" />
                </span>
                <div>
                  <p className="text-xs font-medium">Sample CRA event</p>
                  <p className="text-[11px] text-muted-foreground">Smart Door Lock · v1.1.0</p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-danger/20 bg-danger/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-danger">
                KEV match
              </span>
            </div>
            <div className="px-5 py-6">
              <CountdownDemo />
            </div>
            <div className="grid grid-cols-3 divide-x divide-border/60 border-t border-border/60 bg-muted/20">
              {REPORTS.map((report) => (
                <div key={report.window} className="px-3 py-3.5 text-center">
                  <p className="font-mono text-sm font-semibold tabular">{report.window}</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">{report.label}</p>
                  <p className={`mt-1.5 inline-flex items-center gap-1 text-[10px] font-medium ${report.tone}`}>
                    <span className={`size-1 rounded-full ${report.dot}`} /> {report.state}
                  </p>
                </div>
              ))}
            </div>
          </div>
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.55 }}
            className="absolute -bottom-6 -left-4 hidden items-center gap-2 rounded-xl border border-border/70 bg-card px-3.5 py-2.5 shadow-soft sm:flex lg:-left-8"
          >
            <span className="grid size-7 place-items-center rounded-lg bg-brand/10 text-brand">
              <Sparkles className="size-3.5" />
            </span>
            <div>
              <p className="text-[11px] font-medium">Draft ready in seconds</p>
              <p className="text-[10px] text-muted-foreground">AI or template · human approved</p>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}
