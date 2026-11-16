"use client";

import { useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { motion } from "motion/react";
import { Menu } from "lucide-react";
import { SessionProvider } from "@/lib/session";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { DemoBanner } from "./demo-banner";
import { SidebarNav } from "./sidebar";
import { UserMenu } from "./user-menu";

// Estructura de la app: barra lateral (hoja en móvil), cabecera, franja de demo y animación de entrada de cada página.
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  return (
    <SessionProvider>
      <div className="flex min-h-full flex-1">
        <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 border-r border-border/70 bg-sidebar md:block">
          <SidebarNav />
        </aside>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetContent side="left" className="w-72 bg-sidebar p-0">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            <SidebarNav onNavigate={() => setOpen(false)} />
          </SheetContent>
        </Sheet>
        <div className="flex min-w-0 flex-1 flex-col">
          <DemoBanner />
          <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/70 bg-background/80 px-4 backdrop-blur-md sm:px-6">
            <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open navigation" onClick={() => setOpen(true)}>
              <Menu className="size-5" />
            </Button>
            <div className="ml-auto flex items-center gap-1">
              <ThemeToggle />
              <UserMenu />
            </div>
          </header>
          <motion.main key={pathname} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: "easeOut" }} className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">
            {children}
          </motion.main>
        </div>
      </div>
    </SessionProvider>
  );
}
