import { Deadlines } from "@/components/landing/deadlines";
import { Hero } from "@/components/landing/hero";
import { HowItWorks } from "@/components/landing/how-it-works";
import { RequestAccessForm } from "@/components/landing/request-access-form";
import { Reveal } from "@/components/landing/reveal";
import { SiteFooter } from "@/components/landing/site-footer";
import { SiteHeader } from "@/components/landing/site-header";
import { TryDemoButton } from "@/components/try-demo-button";

// Portada pública: problema del CRA, cómo funciona, demo y lista de espera.
export default function Home() {
  return (
    <>
      <SiteHeader />
      <main className="flex-1">
        <Hero />
        <Deadlines />
        <HowItWorks />
        <section className="border-y border-border/60 bg-muted/30">
          <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-16 sm:px-6 md:flex-row md:items-center md:justify-between">
            <Reveal>
              <h2 className="text-2xl font-semibold tracking-tight">See a real case end to end.</h2>
              <p className="mt-2 max-w-xl text-muted-foreground">
                The demo company confirmed Log4Shell on its smart door lock 30 hours ago. The early warning went out on
                time; the notification clock is still running.
              </p>
            </Reveal>
            <TryDemoButton label="Open the demo" />
          </div>
        </section>
        <section id="access" className="scroll-mt-20">
          <div className="mx-auto grid max-w-6xl gap-10 px-4 py-20 sm:px-6 md:grid-cols-2">
            <Reveal>
              <h2 className="text-3xl font-semibold tracking-tight">Request access</h2>
              <p className="mt-3 text-muted-foreground">
                Accounts are invite-only while CRA Desk is in early access. Tell us what you build and we&apos;ll reach out.
              </p>
            </Reveal>
            <Reveal delay={0.1}>
              <div className="rounded-2xl border border-border/70 bg-card p-6 shadow-sm">
                <RequestAccessForm />
              </div>
            </Reveal>
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
