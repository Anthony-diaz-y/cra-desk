import type { DeadlineStatus } from "./types";

export type Tone = "neutral" | "warning" | "danger" | "success";

const HOUR = 3_600_000;

const LABELS: Record<DeadlineStatus, string> = {
  PENDING: "Pending",
  DUE_SOON: "Due soon",
  OVERDUE: "Overdue",
  SENT_ON_TIME: "Sent on time",
  SENT_LATE: "Sent late",
};

const TONES: Record<DeadlineStatus, Tone> = {
  PENDING: "neutral",
  DUE_SOON: "warning",
  OVERDUE: "danger",
  SENT_ON_TIME: "success",
  SENT_LATE: "danger",
};

// Devuelve los milisegundos que faltan hasta el vencimiento (negativo si ya pasó).
export function remainingMs(dueAt: string, now: number): number {
  return Date.parse(dueAt) - now;
}

// Escribe el tiempo restante como "23h 59m 59s", o "3d 2h" desde 48 horas; nunca negativo.
export function formatRemaining(ms: number): string {
  if (ms <= 0) {
    return "0s";
  }
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  if (ms >= 48 * HOUR) {
    return `${Math.floor(hours / 24)}d ${hours % 24}h`;
  }
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  return `${hours}h ${minutes}m ${totalSeconds % 60}s`;
}

// Devuelve el color que corresponde al estado del plazo.
export function statusTone(status: DeadlineStatus): Tone {
  return TONES[status];
}

// Devuelve el texto en inglés del estado del plazo.
export function statusLabel(status: DeadlineStatus): string {
  return LABELS[status];
}
