import { ThemeToggle } from "@/components/theme-toggle";

// Página de inicio provisional hasta que llegue la portada.
export default function Home() {
  return (
    <main className="flex flex-1 items-center justify-center gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">CRA Desk</h1>
      <ThemeToggle />
    </main>
  );
}
