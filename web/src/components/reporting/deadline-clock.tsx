"use client";

import {
  formatRemaining,
  remainingMs,
  statusLabel,
  statusTone,
  type Tone,
} from "@/lib/clocks";
import { dateTime } from "@/lib/format";
import type { DeadlineStatus } from "@/lib/types";
import { useNow } from "@/lib/use-now";
import { cn } from "@/lib/utils";
import { motion } from "motion/react";

const TONE_TEXT: Record<Tone, string> = {
  neutral: "text-foreground",
  warning: "text-warning",
  danger: "text-danger",
  success: "text-success",
};

const TONE_STROKE: Record<Tone, string> = {
  neutral: "stroke-brand",
  warning: "stroke-warning",
  danger: "stroke-danger",
  success: "stroke-success",
};

const TONE_HALO: Record<Tone, string> = {
  neutral: "ring-brand/10",
  warning: "ring-warning/15",
  danger: "ring-danger/15",
  success: "ring-success/15",
};

type Props = {
  dueAt: string;
  startAt: string;
  status: DeadlineStatus;
  sentAt: string | null;
  size?: "sm" | "lg";
};

// Reloj de un plazo del CRA: cuenta regresiva por segundo con anillo de progreso, o la hora de envío si ya se envió.
export function DeadlineClock({
  dueAt,
  startAt,
  status,
  sentAt,
  size = "sm",
}: Props) {
  const sent = sentAt !== null;
  const now = useNow(1000, !sent);
  const left = remainingMs(dueAt, now);
  const total = Math.max(1, Date.parse(dueAt) - Date.parse(startAt));
  const live: DeadlineStatus = sent ? status : left <= 0 ? "OVERDUE" : left <= total * 0.25 ? "DUE_SOON" : "PENDING";
  const tone = statusTone(live);
  const progress = sent ? 1 : Math.min(1, Math.max(0, left / total));
  const ring = size === "lg" ? "size-28" : "size-16";
  return (
    <div className={cn("flex items-center gap-4", size === "lg" && "flex-col gap-4 text-center")}>
      <div className={cn("relative shrink-0 rounded-full ring-[3px]", ring, TONE_HALO[tone])}>
        <svg
          viewBox="0 0 64 64"
          className="size-full -rotate-90"
          aria-hidden="true"
        >
          <circle
            cx="32"
            cy="32"
            r="28"
            className="fill-none stroke-border"
            strokeWidth="5"
          />
          <motion.circle
            cx="32"
            cy="32"
            r="28"
            className={cn("fill-none", TONE_STROKE[tone])}
            strokeWidth="5"
            strokeLinecap="round"
            pathLength={1}
            initial={false}
            animate={{ pathLength: progress }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          />
        </svg>
        {live === "DUE_SOON" && (
          <span
            className="absolute inset-0 animate-ping rounded-full border border-warning/40 motion-reduce:animate-none"
            aria-hidden="true"
          />
        )}
      </div>
      <div className={cn("min-w-0", size === "lg" && "flex flex-col items-center")}>
        <p
          className={cn(
            "text-[10px] font-semibold uppercase tracking-[0.12em]",
            TONE_TEXT[tone],
          )}
        >
          {statusLabel(live)}
        </p>
        {sent ? (
          <p className="mt-0.5 text-sm text-muted-foreground">
            Sent {dateTime(sentAt)}
          </p>
        ) : (
          <p
            role="timer"
            aria-live="off"
            className={cn(
              "mt-0.5 whitespace-nowrap font-mono font-semibold tabular tracking-tight",
              size === "lg" ? "text-3xl" : "text-lg",
              TONE_TEXT[tone],
            )}
          >
            {formatRemaining(left)}
          </p>
        )}
        <p className="mt-0.5 text-[11px] text-muted-foreground">Due {dateTime(dueAt)}</p>
      </div>
    </div>
  );
}
