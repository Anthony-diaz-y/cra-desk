import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AuthCard } from "@/components/auth/auth-card";
import { LoginForm } from "@/components/auth/login-form";
import { TryDemoButton } from "@/components/try-demo-button";

export const metadata: Metadata = { title: "Sign in" };

// Página de login con el acceso a la demo.
export default function LoginPage() {
  return (
    <AuthCard
      title="Sign in"
      subtitle="Welcome back to CRA Desk."
      footer={<>Have an invitation code? <Link href="/invite" className="font-medium text-foreground underline-offset-4 hover:underline">Create your account</Link></>}
    >
      <Suspense>
        <LoginForm />
      </Suspense>
      <div className="my-5 flex items-center gap-3 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">
        <span className="h-px flex-1 bg-border" />or<span className="h-px flex-1 bg-border" />
      </div>
      <TryDemoButton className="h-10 w-full text-sm" label="Explore the read-only demo" />
    </AuthCard>
  );
}
