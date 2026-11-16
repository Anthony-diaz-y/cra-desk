import { describe, expect, it } from "vitest";
import { buildCsp } from "./csp";

// Devuelve el valor de una directiva de la política.
function directive(csp: string, name: string): string {
  return csp.split(";").map((d) => d.trim()).find((d) => d.startsWith(name + " ")) ?? "";
}

describe("buildCsp", () => {
  it("productionHasNonceAndNoUnsafeInlineScripts", () => {
    const scripts = directive(buildCsp("abc", false), "script-src");
    expect(scripts).toContain("'nonce-abc'");
    expect(scripts).toContain("'strict-dynamic'");
    expect(scripts).not.toContain("'unsafe-inline'");
    expect(scripts).not.toContain("'unsafe-eval'");
  });

  it("devAllowsEval", () => {
    expect(directive(buildCsp("abc", true), "script-src")).toContain("'unsafe-eval'");
  });

  it("forbidsFraming", () => {
    const csp = buildCsp("abc", false);
    expect(directive(csp, "frame-ancestors")).toBe("frame-ancestors 'none'");
    expect(directive(csp, "object-src")).toBe("object-src 'none'");
    expect(directive(csp, "connect-src")).toBe("connect-src 'self'");
  });
});
