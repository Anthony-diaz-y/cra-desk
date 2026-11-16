import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AuthCard } from "@/components/auth/auth-card";
import { InviteForm } from "@/components/auth/invite-form";

export const metadata: Metadata = { title: "Accept invitation" };

// Página para crear la cuenta y la empresa con un código de invitación.
export default function InvitePage() {
  return (
    <AuthCard
      title="Create your account"
      subtitle="Use the invitation code you received to set up your company."
      footer={<>Already have an account? <Link href="/login" className="font-medium text-foreground underline-offset-4 hover:underline">Sign in</Link></>}
    >
      <Suspense>
        <InviteForm />
      </Suspense>
    </AuthCard>
  );
}
