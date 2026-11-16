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
  const live: DeadlineStatus = sent
    ? status
    : left <= 0
      ? "OVERDUE"
      : status === "OVERDUE"
        ? "DUE_SOON"
        : status;
  const tone = statusTone(live);
  const total = Math.max(1, Date.parse(dueAt) - Date.parse(startAt));
  const progress = sent ? 1 : Math.min(1, Math.max(0, left / total));
  const ring = size === "lg" ? "size-28" : "size-16";
  return (
    <div className="flex items-center gap-4">
      <div className={cn("relative shrink-0", ring)}>
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
      <div className="min-w-0">
        <p
          className={cn(
            "text-xs font-medium uppercase tracking-wide",
            TONE_TEXT[tone],
          )}
        >
          {statusLabel(live)}
        </p>
        {sent ? (
          <p className="text-sm text-muted-foreground">
            Sent {dateTime(sentAt)}
          </p>
        ) : (
          <p
            role="timer"
            aria-live="off"
            className={cn(
              "font-mono font-semibold tabular",
              size === "lg" ? "text-3xl" : "text-lg",
              TONE_TEXT[tone],
            )}
          >
            {formatRemaining(left)}
          </p>
        )}
        <p className="text-xs text-muted-foreground">Due {dateTime(dueAt)}</p>
      </div>
    </div>
  );
}
