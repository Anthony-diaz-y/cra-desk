import { NextResponse, type NextRequest } from "next/server";
import { buildCsp } from "@/lib/csp";
import { loginRedirect } from "@/lib/routes";

// Manda /app al login si no hay cookie de sesión; si no, genera el nonce de la CSP y se lo pasa al render.
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if ((pathname === "/app" || pathname.startsWith("/app/")) && !request.cookies.has("JSESSIONID")) {
    return NextResponse.redirect(new URL(loginRedirect(pathname + search), request.url));
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  const nonce = btoa(String.fromCharCode(...bytes));
  const csp = buildCsp(nonce, process.env.NODE_ENV === "development");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

export const config = {
  matcher: [
    {
      source: "/((?!api|actuator|_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
