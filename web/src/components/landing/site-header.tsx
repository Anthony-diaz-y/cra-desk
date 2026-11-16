import Link from "next/link";
import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Cabecera fija de la portada con la marca, enlaces a las secciones, tema y acceso.
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/75 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link href="/" aria-label="CRA Desk home">
          <Logo />
        </Link>
        <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex" aria-label="Sections">
          <a href="#deadlines" className="hover:text-foreground">Deadlines</a>
          <a href="#how-it-works" className="hover:text-foreground">How it works</a>
          <a href="#access" className="hover:text-foreground">Request access</a>
        </nav>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Link href="/login" className={cn(buttonVariants({ variant: "outline" }), "h-9 px-3")}>
            Sign in
          </Link>
        </div>
      </div>
    </header>
  );
}
