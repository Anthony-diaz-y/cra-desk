"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Loader2, ShieldAlert, ShieldCheck } from "lucide-react";
import { apiFetch } from "@/lib/api";
import type { AuditVerification } from "@/lib/types";
import { Button } from "@/components/ui/button";

// Botón que recalcula la cadena de hashes de la auditoría y muestra si está intacta.
export function VerifyChain() {
  const [result, setResult] = useState<AuditVerification | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Pide a la API que verifique la cadena.
  async function verify() {
    setBusy(true);
    setError(null);
    try {
      setResult(await apiFetch<AuditVerification>("/api/audit/verify"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Verification failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button variant="outline" onClick={verify} disabled={busy} className="gap-1.5">
        {busy ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />} Verify chain
      </Button>
      <AnimatePresence mode="wait">
        {result && (
          <motion.p key={`${result.valid}-${result.entries}`} role="status" initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}
            className={result.valid ? "inline-flex items-center gap-1.5 text-sm text-success" : "inline-flex items-center gap-1.5 text-sm text-danger"}>
            {result.valid ? <ShieldCheck className="size-4" /> : <ShieldAlert className="size-4" />}
            {result.valid ? `Chain intact — ${result.entries} entries` : `Entry #${result.firstInvalidSeq} was altered or removed`}
          </motion.p>
        )}
      </AnimatePresence>
      {error && <p role="alert" className="text-sm text-danger">{error}</p>}
    </div>
  );
}
