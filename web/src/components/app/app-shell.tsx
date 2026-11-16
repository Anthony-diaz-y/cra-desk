"use client";

import { useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import { ChevronRight, Menu } from "lucide-react";
import { SessionProvider } from "@/lib/session";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { DemoBanner } from "./demo-banner";
import { SidebarNav } from "./sidebar";
import { UserMenu } from "./user-menu";

const SECTIONS: { match: string; label: string }[] = [
  { match: "/app/products", label: "Products" },
  { match: "/app/findings", label: "Findings" },
  { match: "/app/audit", label: "Audit log" },
  { match: "/app/settings", label: "Settings" },
];

// Devuelve el nombre de la sección de la cabecera a partir de la ruta.
function sectionLabel(pathname: string): string {
  return SECTIONS.find((section) => pathname.startsWith(section.match))?.label ?? "Deadlines";
}

// Estructura de la app: barra lateral (hoja en móvil), cabecera, franja de demo y animación de entrada de cada página.
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  return (
    <SessionProvider>
      <div className="flex min-h-full flex-1">
        <aside className="sticky top-0 hidden h-dvh w-[272px] shrink-0 border-r border-sidebar-border bg-sidebar md:block">
          <SidebarNav />
        </aside>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetContent side="left" className="w-[288px] bg-sidebar p-0">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            <SidebarNav onNavigate={() => setOpen(false)} />
          </SheetContent>
        </Sheet>
        <div className="flex min-w-0 flex-1 flex-col">
          <DemoBanner />
          <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-border/60 bg-background/80 px-4 supports-backdrop-filter:backdrop-blur-xl sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-2">
              <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open navigation" onClick={() => setOpen(true)}>
                <Menu className="size-5" />
              </Button>
              <div className="hidden items-center gap-2 text-sm md:flex">
                <span className="text-muted-foreground">Workspace</span>
                <ChevronRight className="size-3.5 text-muted-foreground/50" />
                <span className="truncate font-medium">{sectionLabel(pathname)}</span>
              </div>
            </div>
            <div className="ml-auto flex items-center gap-1.5">
              <span className="mr-1 hidden items-center gap-1.5 rounded-full border border-border/70 bg-card/70 px-2.5 py-1 text-[11px] font-medium text-muted-foreground lg:inline-flex">
                <span className="relative flex size-1.5">
                  <span className="absolute inline-flex size-full animate-ping rounded-full bg-success/60 motion-reduce:animate-none" />
                  <span className="relative inline-flex size-1.5 rounded-full bg-success" />
                </span>
                CRA clocks live
              </span>
              <ThemeToggle />
              <span className="mx-1 hidden h-5 w-px bg-border sm:block" aria-hidden="true" />
              <UserMenu />
            </div>
          </header>
          <main className="relative flex-1">
            <div
              className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-[radial-gradient(70%_100%_at_50%_0%,color-mix(in_oklch,var(--brand)_5%,transparent),transparent_75%)]"
              aria-hidden="true"
            />
            <motion.div
              key={pathname}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              className="relative mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-10 lg:py-10"
            >
              {children}
            </motion.div>
          </main>
        </div>
      </div>
    </SessionProvider>
  );
}
