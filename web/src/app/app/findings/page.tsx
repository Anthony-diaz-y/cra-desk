import type { Metadata } from "next";
import { FindingsList } from "@/components/reporting/findings-list";

export const metadata: Metadata = { title: "Findings" };

// Página de hallazgos por revisar.
export default function FindingsPage() {
  return <FindingsList />;
}
