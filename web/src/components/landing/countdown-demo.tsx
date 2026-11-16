"use client";

import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { Flame } from "lucide-react";

const START = 24 * 3600 - 1;

// Escribe segundos como hh:mm:ss.
function clock(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return [h, m, s].map((n) => String(n).padStart(2, "0")).join(":");
}

// Reloj decorativo de 24 horas que corre hacia atrás, como el plazo del aviso temprano.
export function CountdownDemo() {
  const [seconds, setSeconds] = useState(START);
  useEffect(() => {
    const id = setInterval(() => setSeconds((s) => (s <= 0 ? START : s - 1)), 1000);
    return () => clearInterval(id);
  }, []);
  const progress = seconds / START;
  return (
    <div className="relative mx-auto aspect-square w-full max-w-[300px]">
      <div
        className="pointer-events-none absolute inset-6 rounded-full bg-brand/10 blur-2xl animate-pulse-soft"
        aria-hidden="true"
      />
      <svg viewBox="0 0 140 140" className="absolute inset-0 size-full -rotate-90" aria-hidden="true">
        <defs>
          <linearGradient id="countdown-ring" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="var(--brand)" stopOpacity="0.5" />
            <stop offset="100%" stopColor="var(--brand)" />
          </linearGradient>
        </defs>
        <circle cx="70" cy="70" r="66" className="fill-none stroke-border/60" strokeWidth="1" strokeDasharray="1 5" />
        <circle cx="70" cy="70" r="58" className="fill-none stroke-border" strokeWidth="5" />
        <motion.circle
          cx="70"
          cy="70"
          r="58"
          className="fill-none"
          stroke="url(#countdown-ring)"
          strokeWidth="5"
          strokeLinecap="round"
          pathLength={1}
          initial={false}
          animate={{ pathLength: progress }}
          transition={{ duration: 0.8, ease: "easeOut" }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-8 text-center">
        <span className="inline-flex items-center gap-1.5 rounded-full border border-brand/20 bg-brand/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-brand">
          <span className="relative flex size-1.5">
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand/70 motion-reduce:animate-none" />
            <span className="relative inline-flex size-1.5 rounded-full bg-brand" />
          </span>
          Live clock
        </span>
        <span className="text-[10px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
          Early warning due in
        </span>
        <span
          role="timer"
          aria-label="Early warning countdown"
          className="font-mono text-[2.6rem] font-semibold leading-none tracking-tight tabular text-foreground"
        >
          {clock(seconds)}
        </span>
        <span className="h-px w-10 bg-border" />
        <span className="text-[10px] leading-relaxed text-muted-foreground">
          CVE-2021-44228 · Smart Door Lock 1.1.0
        </span>
        <span className="inline-flex items-center gap-1 rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-medium text-danger">
          <Flame className="size-2.5" /> In CISA KEV
        </span>
      </div>
    </div>
  );
}
