import type { Metadata } from "next";
import { PageHeader } from "@/components/app/page-header";
import { AuditTable } from "@/components/audit/audit-table";
import { VerifyChain } from "@/components/audit/verify-chain";

export const metadata: Metadata = { title: "Audit log" };

// Página del registro de auditoría con su verificación.
export default function AuditPage() {
  return (
    <>
      <PageHeader title="Audit log" description="Every action is appended to a SHA-256 hash chain for your company. Entries can't be edited or deleted, and any tampering breaks the chain." actions={<VerifyChain />} />
      <AuditTable />
    </>
  );
}
