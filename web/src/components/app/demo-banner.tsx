"use client";

import Link from "next/link";
import { Eye } from "lucide-react";
import { useSession } from "@/lib/session";

// Franja superior que avisa que es la demo de solo lectura y ofrece pedir acceso.
export function DemoBanner() {
  const { isDemo } = useSession();
  if (!isDemo) {
    return null;
  }
  return (
    <div className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 border-b border-brand/20 bg-brand/10 px-4 py-2 text-center text-sm">
      <span className="inline-flex items-center gap-1.5 font-medium text-brand">
        <Eye className="size-4" /> Read-only demo
      </span>
      <span className="text-muted-foreground">Explore freely — changes are disabled.</span>
      <Link href="/#access" className="font-medium underline-offset-4 hover:underline">Request access</Link>
    </div>
  );
}
