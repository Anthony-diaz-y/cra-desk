"use client";

import { useEffect, useState } from "react";
import { motion } from "motion/react";

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
    <div className="relative mx-auto aspect-square w-full max-w-[320px]">
      <svg viewBox="0 0 120 120" className="absolute inset-0 size-full -rotate-90" aria-hidden="true">
        <circle cx="60" cy="60" r="54" className="fill-none stroke-border" strokeWidth="4" />
        <motion.circle
          cx="60"
          cy="60"
          r="54"
          className="fill-none stroke-brand"
          strokeWidth="4"
          strokeLinecap="round"
          pathLength={1}
          initial={false}
          animate={{ pathLength: progress }}
          transition={{ duration: 0.8, ease: "easeOut" }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-1">
        <span className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">Early warning due in</span>
        <span role="timer" aria-label="Early warning countdown" className="font-mono text-4xl font-semibold tabular sm:text-5xl">
          {clock(seconds)}
        </span>
        <span className="text-xs text-muted-foreground">CVE-2021-44228 · Smart Door Lock 1.1.0</span>
      </div>
    </div>
  );
}
