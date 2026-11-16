import type { Metadata } from "next";
import { headers } from "next/headers";
import { Geist, Geist_Mono } from "next/font/google";
import { Providers } from "./providers";
import "./globals.css";

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

export const metadata: Metadata = {
  title: { default: "CRA Desk — Cyber Resilience Act reporting", template: "%s · CRA Desk" },
  description:
    "Track SBOMs, catch actively exploited vulnerabilities and draft your EU Cyber Resilience Act reports to ENISA on time.",
};

// Estructura raíz: fuentes, tema y proveedores, con el nonce de la CSP de esta petición.
export default async function RootLayout({ children }: LayoutProps<"/">) {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return (
    <html lang="en" suppressHydrationWarning className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">
        <Providers nonce={nonce}>{children}</Providers>
      </body>
    </html>
  );
}
