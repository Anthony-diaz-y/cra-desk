import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { resetNavigation } from "@/test/navigation-mock";
import { DEMO, MEMBER, mockApi, renderWithSession } from "@/test/render";
import { Button } from "@/components/ui/button";
import { CanWrite } from "@/lib/session";
import { DemoBanner } from "./demo-banner";

describe("app shell", () => {
  beforeEach(() => {
    resetNavigation();
    mockApi([]);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("demoBannerOnlyForDemo", () => {
    const { unmount } = renderWithSession(<DemoBanner />, DEMO);
    expect(screen.getByText(/read-only demo/i)).toBeInTheDocument();
    unmount();

    renderWithSession(<DemoBanner />, MEMBER);
    expect(screen.queryByText(/read-only demo/i)).not.toBeInTheDocument();
  });

  it("canWriteDisablesForDemo", () => {
    const { unmount } = renderWithSession(<CanWrite><Button>Create product</Button></CanWrite>, DEMO);
    expect(screen.getByRole("button", { name: /create product/i })).toBeDisabled();
    unmount();

    renderWithSession(<CanWrite><Button>Create product</Button></CanWrite>, MEMBER);
    expect(screen.getByRole("button", { name: /create product/i })).toBeEnabled();
  });
});
