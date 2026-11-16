"use client";

import { motion } from "motion/react";
import { TryDemoButton } from "@/components/try-demo-button";
import { CountdownDemo } from "./countdown-demo";

// Portada principal: el mensaje, los botones de demo y acceso, y el reloj de 24 horas.
export function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div className="bg-grid pointer-events-none absolute inset-0 [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)]" aria-hidden="true" />
      <div className="pointer-events-none absolute -top-40 left-1/2 h-[480px] w-[720px] -translate-x-1/2 rounded-full bg-brand/15 blur-3xl" aria-hidden="true" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 py-20 sm:px-6 md:grid-cols-[1.1fr_0.9fr] md:py-28">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}>
          <span className="inline-flex items-center gap-2 rounded-full border border-brand/30 bg-brand/10 px-3 py-1 text-xs font-medium text-brand">
            <span className="size-1.5 rounded-full bg-brand" />
            EU Cyber Resilience Act · reporting applies from 11 September 2026
          </span>
          <h1 className="mt-6 text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-6xl">
            Report actively exploited vulnerabilities to ENISA <span className="text-brand">on time.</span>
          </h1>
          <p className="mt-5 max-w-xl text-lg text-pretty text-muted-foreground">
            CRA Desk watches the SBOMs of your connected products, flags vulnerabilities that attackers are already
            exploiting, and helps you draft the 24-hour, 72-hour and 14-day reports — with a person approving every word.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <TryDemoButton />
            <a href="#access" className="inline-flex h-11 items-center rounded-lg border border-border px-5 text-base font-medium hover:bg-muted">
              Request access
            </a>
          </div>
          <p className="mt-4 text-sm text-muted-foreground">No sign-up needed for the demo. Read-only, with sample data.</p>
        </motion.div>
        <motion.div initial={{ opacity: 0, scale: 0.94 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.7, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
          className="rounded-3xl border border-border/70 bg-card/70 p-8 shadow-xl shadow-brand/5 backdrop-blur">
          <CountdownDemo />
        </motion.div>
      </div>
    </section>
  );
}
