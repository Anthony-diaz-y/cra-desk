import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "#deadlines", label: "Deadlines" },
  { href: "#how-it-works", label: "How it works" },
  { href: "#access", label: "Request access" },
];

// Cabecera fija de la portada con la marca, enlaces a las secciones, tema y acceso.
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/80 supports-backdrop-filter:backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-6 px-4 sm:px-6 lg:px-8">
        <Link href="/" aria-label="CRA Desk home" className="shrink-0 transition-opacity hover:opacity-80">
          <Logo />
        </Link>
        <nav className="hidden items-center gap-1 md:flex" aria-label="Sections">
          {NAV.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="group relative rounded-full px-3.5 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground"
            >
              {item.label}
              <span className="absolute inset-x-3.5 -bottom-px h-px scale-x-0 bg-brand/60 transition-transform duration-300 group-hover:scale-x-100" />
            </a>
          ))}
        </nav>
        <div className="flex items-center gap-1.5">
          <ThemeToggle />
          <Link href="/login" className={cn(buttonVariants({ variant: "ghost" }), "h-9 px-3 text-sm")}>
            Sign in
          </Link>
          <Link
            href="#access"
            className={cn(
              buttonVariants({ variant: "default" }),
              "hidden h-9 gap-1.5 bg-brand px-3.5 text-brand-foreground shadow-sm shadow-brand/20 hover:bg-brand/90 sm:inline-flex",
            )}
          >
            Request access
            <ArrowUpRight className="size-3.5" />
          </Link>
        </div>
      </div>
    </header>
  );
}
