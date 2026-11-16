import type { Metadata } from "next";
import { Overview } from "@/components/reporting/overview";

export const metadata: Metadata = { title: "Deadlines" };

// Página principal de la app: el panel de plazos.
export default function OverviewPage() {
  return <Overview />;
}
