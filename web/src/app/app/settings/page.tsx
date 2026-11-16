import type { Metadata } from "next";
import { PageHeader } from "@/components/app/page-header";
import { ApiKeys } from "@/components/settings/api-keys";

export const metadata: Metadata = { title: "Settings" };

// Página de ajustes con las claves de API.
export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Manage the keys that let your CI pipeline upload SBOMs and trigger vulnerability scans." />
      <ApiKeys />
    </>
  );
}
