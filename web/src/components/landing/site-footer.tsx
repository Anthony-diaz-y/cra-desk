import { Logo } from "@/components/logo";

// Pie de la portada con el aviso legal y el enlace al repositorio.
export function SiteFooter() {
  return (
    <footer className="border-t border-border/60">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-10 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <Logo className="text-foreground" />
        <p>Support tool, not legal advice. CRA Desk prepares reports; you submit them to ENISA.</p>
        <a href="https://github.com/Anthony-diaz-y/cra-desk" className="hover:text-foreground" rel="noreferrer">
          Source on GitHub
        </a>
      </div>
    </footer>
  );
}
