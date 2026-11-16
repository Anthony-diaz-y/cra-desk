"use client";

import { useEffect, useState } from "react";

// Devuelve la hora actual en milisegundos y la actualiza cada intervalo (un segundo por defecto).
export function useNow(intervalMs = 1000, enabled = true): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!enabled) {
      return;
    }
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs, enabled]);
  return now;
}
