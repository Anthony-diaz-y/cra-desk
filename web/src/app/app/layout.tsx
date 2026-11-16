import { AppShell } from "@/components/app/app-shell";

// Estructura común de todas las páginas de la app.
export default function AppLayout({ children }: LayoutProps<"/app">) {
  return <AppShell>{children}</AppShell>;
}
