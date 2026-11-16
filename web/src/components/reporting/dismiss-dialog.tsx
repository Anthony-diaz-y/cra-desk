"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, apiFetch } from "@/lib/api";
import { CanWrite } from "@/lib/session";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2, XCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";

const MIN = 10;
const MAX = 1000;

// Botón y diálogo para descartar un hallazgo con un motivo, que queda en la auditoría.
export function DismissDialog({
  findingId,
  cveId,
}: {
  findingId: string;
  cveId: string;
}) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  // Valida el motivo y descarta el hallazgo en la API.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (reason.trim().length < MIN) {
      setError(`Explain why in at least ${MIN} characters.`);
      return;
    }
    setSending(true);
    setError(null);
    try {
      await apiFetch(`/api/findings/${findingId}/dismiss`, {
        method: "POST",
        json: { reason: reason.trim() },
      });
      await Promise.all([client.invalidateQueries({ queryKey: ["findings"] }), client.invalidateQueries({ queryKey: ["audit"] })]);
      toast.success(`${cveId} dismissed`);
      setOpen(false);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (e.fieldErrors.reason ?? e.message)
          : "Could not dismiss the finding.",
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <>
      <CanWrite>
        <Button
          variant="outline"
          onClick={() => setOpen(true)}
          className="gap-1.5"
        >
          <XCircle className="size-4" /> Dismiss
        </Button>
      </CanWrite>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Dismiss {cveId}?</DialogTitle>
            <DialogDescription>
              For example: the vulnerable code path is not reachable in your
              product. The reason is kept in the audit log.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} noValidate className="grid gap-3">
            <Label htmlFor={`dismiss-${findingId}`}>Reason</Label>
            <Textarea
              id={`dismiss-${findingId}`}
              rows={4}
              maxLength={MAX}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              aria-invalid={!!error}
            />
            <p className="text-right text-xs text-muted-foreground tabular">
              {reason.trim().length}/{MAX}
            </p>
            {error && (
              <p role="alert" className="text-sm text-danger">
                {error}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" variant="destructive" disabled={sending}>
                {sending && <Loader2 className="size-4 animate-spin" />}
                Dismiss finding
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
