"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Boxes, ClipboardCheck, LayoutDashboard, ScrollText, Settings, ShieldCheck } from "lucide-react";
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
  const initial = me?.organizationName?.trim().charAt(0).toUpperCase() ?? "…";
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 shrink-0 items-center border-b border-sidebar-border px-5">
        <Link href="/app" onClick={onNavigate} aria-label="Overview" className="transition-opacity hover:opacity-80">
          <Logo />
        </Link>
      </div>
      <div className="px-3 pt-4">
        <div className="rounded-2xl border border-sidebar-border bg-background/60 p-3 shadow-sm">
          <div className="flex items-center gap-2.5">
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand/10 text-xs font-semibold text-brand ring-1 ring-brand/15 ring-inset">
              {initial}
            </span>
            <div className="min-w-0">
              <p className="text-[10px] font-medium uppercase tracking-[0.12em] text-muted-foreground">Company</p>
              <p className="truncate text-sm font-medium">{me?.organizationName ?? "…"}</p>
            </div>
          </div>
        </div>
      </div>
      <nav className="mt-6 grid gap-1 px-3" aria-label="Main">
        <p className="px-3 pb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground/70">Workspace</p>
        {NAV.map((item) => {
          const active = isActive(pathname, item.href);
          const count = item.href === "/app/findings" ? (pending?.length ?? 0) : 0;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors",
                active ? "text-foreground" : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-foreground",
              )}
            >
              {active && (
                <motion.span
                  layoutId="nav-active"
                  className="absolute inset-0 rounded-xl border border-brand/10 bg-brand/[0.07] shadow-sm"
                  transition={{ type: "spring", bounce: 0.12, duration: 0.45 }}
                />
              )}
              <item.icon
                className={cn("relative size-4 transition-colors", active ? "text-brand" : "text-muted-foreground group-hover:text-foreground")}
              />
              <span className="relative flex-1 font-medium">{item.label}</span>
              {count > 0 && (
                <span className="relative inline-flex min-w-5 items-center justify-center rounded-full bg-warning/20 px-1.5 py-0.5 text-[10px] font-semibold text-foreground tabular">
                  {count}
                </span>
              )}
              {active && (
                <motion.span
                  layoutId="nav-active-bar"
                  className="absolute -left-3 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-brand"
                  transition={{ type: "spring", bounce: 0.12, duration: 0.45 }}
                />
              )}
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto p-3">
        <div className="relative overflow-hidden rounded-2xl border border-border/70 bg-background/70 p-4">
          <div className="pointer-events-none absolute -right-6 -top-6 size-20 rounded-full bg-brand/[0.06] blur-xl" aria-hidden="true" />
          <div className="relative flex items-center gap-2 text-xs font-medium">
            <span className="grid size-6 place-items-center rounded-lg bg-brand/10 text-brand">
              <ShieldCheck className="size-3.5" />
            </span>
            CRA Article 14
          </div>
          <p className="relative mt-2 text-[11px] leading-relaxed text-muted-foreground">
            Every review, draft and approval is written to a tamper-evident audit log.
          </p>
        </div>
        <p className="mt-3 px-1 text-[11px] text-muted-foreground">Support tool, not legal advice.</p>
      </div>
    </div>
  );
}
