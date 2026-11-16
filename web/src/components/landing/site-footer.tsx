import Link from "next/link";
import { Logo } from "@/components/logo";

// Pie de la portada con el aviso legal y el enlace al repositorio.
export function SiteFooter() {
  return (
    <footer className="relative border-t border-border/60 bg-card/40">
      <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="grid gap-10 md:grid-cols-[1.5fr_0.8fr_0.8fr]">
          <div>
            <Logo />
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-muted-foreground">
              A focused compliance desk for small manufacturers of connected products. Built around CRA Article 14.
            </p>
            <p className="mt-4 text-xs text-muted-foreground">
              Support tool, not legal advice. CRA Desk prepares reports; you submit them to ENISA.
            </p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Product</p>
            <nav className="mt-4 grid gap-2.5 text-sm" aria-label="Footer product">
              <a href="#deadlines" className="w-fit text-muted-foreground transition-colors hover:text-foreground">Deadlines</a>
              <a href="#how-it-works" className="w-fit text-muted-foreground transition-colors hover:text-foreground">How it works</a>
              <a href="#access" className="w-fit text-muted-foreground transition-colors hover:text-foreground">Request access</a>
              <Link href="/login" className="w-fit text-muted-foreground transition-colors hover:text-foreground">Sign in</Link>
            </nav>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">Resources</p>
            <nav className="mt-4 grid gap-2.5 text-sm" aria-label="Footer resources">
              <a
                href="https://github.com/Anthony-diaz-y/cra-desk"
                className="w-fit text-muted-foreground transition-colors hover:text-foreground"
                rel="noreferrer"
                target="_blank"
              >
                Source on GitHub
              </a>
              <span className="text-muted-foreground">Regulation (EU) 2024/2847</span>
              <span className="text-muted-foreground">CycloneDX · OSV · CISA KEV</span>
            </nav>
          </div>
        </div>
        <div className="mt-12 flex flex-col gap-3 border-t border-border/60 pt-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} CRA Desk. All rights reserved.</p>
          <p className="inline-flex items-center gap-1.5">
            <span className="size-1.5 rounded-full bg-success" /> Reporting applies from 11 September 2026
          </p>
        </div>
      </div>
    </footer>
  );
}
