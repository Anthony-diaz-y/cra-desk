"use client";

import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, Copy, KeyRound, Loader2, Plus, ShieldCheck, Terminal } from "lucide-react";
import { toast } from "sonner";
import { ApiError, apiFetch } from "@/lib/api";
import { date, dateTime } from "@/lib/format";
import { CanWrite, useSession } from "@/lib/session";
import type { ApiKey, ApiKeyCreated } from "@/lib/types";
import { Field } from "@/components/auth/field";
import { EmptyState, LoadError } from "@/components/app/page-header";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";

// Muestra la clave recién creada una sola vez, con botón para copiarla y el comando de la CLI.
function NewKeyPanel({ created, onDone }: { created: ApiKeyCreated; onDone: () => void }) {
  const [copied, setCopied] = useState(false);

  // Copia la clave al portapapeles.
  async function copy() {
    await navigator.clipboard.writeText(created.key);
    setCopied(true);
  }

  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">Copy it now. For your security it won&apos;t be shown again.</p>
      <div className="flex items-center gap-2 rounded-xl border border-brand/20 bg-brand/[0.05] p-3">
        <code className="flex-1 break-all font-mono text-sm">{created.key}</code>
        <Button variant="ghost" size="icon-sm" aria-label="Copy key" onClick={copy}>{copied ? <Check className="size-4 text-success" /> : <Copy className="size-4" />}</Button>
      </div>
      <div className="rounded-xl border border-border/60 bg-muted/35 p-3.5 font-mono text-[11px] leading-relaxed text-muted-foreground">
        <p className="mb-1.5 flex items-center gap-1.5 font-sans text-xs font-medium text-foreground"><Terminal className="size-3.5" /> Use it in CI</p>
        <p>export CRADESK_API_KEY=&lt;the key above&gt;</p>
        <p>cradesk upload --product &quot;Smart Lock&quot; --version 1.0.0 --file sbom.cdx.json --wait</p>
      </div>
      <DialogFooter>
        <Button onClick={onDone}>I saved it</Button>
      </DialogFooter>
    </div>
  );
}

// Gestión de claves de API para la CLI: crear (se muestra una vez), listar y revocar.
export function ApiKeys() {
  const client = useQueryClient();
  const { isDemo, isLoading: sessionLoading } = useSession();
  const { data, error, isLoading } = useQuery({ queryKey: ["api-keys"], queryFn: () => apiFetch<ApiKey[]>("/api/api-keys"), enabled: !isDemo && !sessionLoading });
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [created, setCreated] = useState<ApiKeyCreated | null>(null);
  const [nameError, setNameError] = useState<string | undefined>();
  const [saving, setSaving] = useState(false);
  const [revoking, setRevoking] = useState<ApiKey | null>(null);
  const [revokeError, setRevokeError] = useState<string | null>(null);
  const [revokeBusy, setRevokeBusy] = useState(false);

  // Crea la clave en la API y la guarda solo en memoria para mostrarla una vez.
  async function create(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setNameError(undefined);
    try {
      setCreated(await apiFetch<ApiKeyCreated>("/api/api-keys", { method: "POST", json: { name: name.trim() } }));
      setName("");
      await Promise.all([client.invalidateQueries({ queryKey: ["api-keys"] }), client.invalidateQueries({ queryKey: ["audit"] })]);
    } catch (e) {
      setNameError(e instanceof ApiError ? (e.fieldErrors.name ?? e.message) : "Could not create the key.");
    } finally {
      setSaving(false);
    }
  }

  // Cierra el diálogo y olvida la clave creada.
  function closeCreate() {
    setCreating(false);
    setCreated(null);
  }

  // Revoca la clave elegida en la API y muestra el error en el diálogo si falla.
  async function revoke() {
    if (!revoking) {
      return;
    }
    setRevokeBusy(true);
    setRevokeError(null);
    try {
      await apiFetch(`/api/api-keys/${revoking.id}`, { method: "DELETE" });
      toast.success(`${revoking.name} revoked`);
      setRevoking(null);
      await Promise.all([client.invalidateQueries({ queryKey: ["api-keys"] }), client.invalidateQueries({ queryKey: ["audit"] })]);
    } catch (e) {
      setRevokeError(e instanceof ApiError ? e.message : "Could not revoke the key.");
    } finally {
      setRevokeBusy(false);
    }
  }

  return (
    <section className="grid gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-medium tracking-tight">API keys</h2>
          <p className="mt-0.5 text-sm text-muted-foreground">For the cradesk CLI and the GitHub Action. Only a hash of each key is stored.</p>
        </div>
        <CanWrite>
          <Button onClick={() => setCreating(true)} className="gap-1.5"><Plus className="size-4" /> New key</Button>
        </CanWrite>
      </div>
      <div className="flex items-start gap-3 rounded-2xl border border-brand/15 bg-brand/[0.04] p-4">
        <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand/10 text-brand">
          <ShieldCheck className="size-4" />
        </span>
        <p className="text-xs leading-relaxed text-muted-foreground">
          Keys are shown once and never stored in plain text. Revoke a key immediately if it leaks — pipelines using it
          will start receiving <span className="font-mono text-foreground">401</span> right away.
        </p>
      </div>
      {sessionLoading ? <Skeleton className="h-24 rounded-2xl" /> : isDemo ? (
        <EmptyState icon={<KeyRound className="size-5" />} title="API keys are not available in the demo" text="With an account you can create keys for the cradesk CLI and the GitHub Action." />
      ) : error ? <LoadError error={error} /> : isLoading ? <Skeleton className="h-24 rounded-2xl" /> : !data?.length ? (
        <EmptyState icon={<KeyRound className="size-5" />} title="No API keys" text="Create one to upload SBOMs from your CI pipeline." />
      ) : (
        <ul className="grid gap-3">
          {data.map((k) => (
            <li key={k.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border/70 bg-card p-4 shadow-sm">
              <div className="flex min-w-0 items-center gap-3">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl border border-border/60 bg-muted/35 text-muted-foreground">
                  <KeyRound className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-2 font-medium">
                    {k.name}
                    <code className="rounded-full border border-border/60 bg-muted/40 px-2 py-0.5 font-mono text-[10px] font-normal text-muted-foreground">crd_{k.prefix}_…</code>
                  </p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[11px] text-muted-foreground">
                    <span className="inline-flex items-center gap-1"><CalendarDays className="size-3" /> Created {date(k.createdAt)}</span>
                    <span>·</span>
                    <span>Last used {k.lastUsedAt ? dateTime(k.lastUsedAt) : "never"}</span>
                  </p>
                </div>
              </div>
              {k.revokedAt ? (
                <span className="rounded-full border border-border bg-muted px-2.5 py-1 text-xs text-muted-foreground">Revoked {date(k.revokedAt)}</span>
              ) : (
                <CanWrite>
                  <Button variant="ghost" size="sm" className="text-danger hover:bg-danger/10 hover:text-danger" onClick={() => setRevoking(k)}>Revoke</Button>
                </CanWrite>
              )}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={creating} onOpenChange={(open) => !open && closeCreate()}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{created ? "Your new API key" : "New API key"}</DialogTitle>
            {!created && <DialogDescription>Give it a name you&apos;ll recognise, like the pipeline that uses it.</DialogDescription>}
          </DialogHeader>
          {created ? <NewKeyPanel created={created} onDone={closeCreate} /> : (
            <form onSubmit={create} noValidate className="grid gap-4">
              <Field id="key-name" label="Key name" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} error={nameError} />
              <Button type="submit" disabled={saving || !name.trim()}>{saving && <Loader2 className="size-4 animate-spin" />}Create key</Button>
            </form>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={revoking !== null} onOpenChange={(open) => { if (!open) { setRevoking(null); setRevokeError(null); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Revoke {revoking?.name}?</DialogTitle>
            <DialogDescription>Pipelines using this key will get 401 right away. This can&apos;t be undone.</DialogDescription>
          </DialogHeader>
          {revokeError && <p role="alert" className="text-sm text-danger">{revokeError}</p>}
          <DialogFooter>
            <Button variant="outline" onClick={() => setRevoking(null)}>Cancel</Button>
            <Button variant="destructive" onClick={revoke} disabled={revokeBusy}>{revokeBusy && <Loader2 className="size-4 animate-spin" />}Revoke key</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}
