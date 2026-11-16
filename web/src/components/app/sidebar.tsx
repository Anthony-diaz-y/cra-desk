"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Boxes, ClipboardCheck, LayoutDashboard, ScrollText, Settings } from "lucide-react";
import { motion } from "motion/react";
import { apiFetch } from "@/lib/api";
import { useSession } from "@/lib/session";
import type { Finding } from "@/lib/types";
import { Logo } from "@/components/logo";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/app", label: "Overview", icon: LayoutDashboard },
  { href: "/app/products", label: "Products", icon: Boxes },
  { href: "/app/findings", label: "Findings", icon: ClipboardCheck },
  { href: "/app/audit", label: "Audit log", icon: ScrollText },
  { href: "/app/settings", label: "Settings", icon: Settings },
];

// Indica si un enlace de la barra corresponde a la página actual.
function isActive(pathname: string, href: string): boolean {
  return href === "/app" ? pathname === "/app" || pathname.startsWith("/app/events") : pathname.startsWith(href);
}

// Barra de navegación de la app con la empresa y el número de hallazgos pendientes.
export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const { me } = useSession();
  const { data: pending } = useQuery({
    queryKey: ["findings", "PENDING_REVIEW"],
    queryFn: () => apiFetch<Finding[]>("/api/findings?status=PENDING_REVIEW"),
  });
  return (
    <div className="flex h-full flex-col gap-6 p-4">
      <Link href="/app" onClick={onNavigate} aria-label="Overview">
        <Logo />
      </Link>
      <div className="rounded-lg border border-border/70 bg-background/60 px-3 py-2">
        <p className="text-xs text-muted-foreground">Company</p>
        <p className="truncate text-sm font-medium">{me?.organizationName ?? "…"}</p>
      </div>
      <nav className="grid gap-1" aria-label="Main">
        {NAV.map((item) => {
          const active = isActive(pathname, item.href);
          const count = item.href === "/app/findings" ? (pending?.length ?? 0) : 0;
          return (
            <Link key={item.href} href={item.href} onClick={onNavigate} aria-current={active ? "page" : undefined}
              className={cn("relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                active ? "text-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground")}>
              {active && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-lg bg-muted" transition={{ type: "spring", bounce: 0.15, duration: 0.4 }} />}
              <item.icon className="relative size-4" />
              <span className="relative flex-1">{item.label}</span>
              {count > 0 && (
                <span className="relative rounded-full bg-warning/20 px-2 text-xs font-medium text-foreground tabular">{count}</span>
              )}
            </Link>
          );
        })}
      </nav>
      <p className="mt-auto text-xs text-muted-foreground">Support tool, not legal advice.</p>
    </div>
  );
}
