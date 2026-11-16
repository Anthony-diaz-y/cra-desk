import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { resetNavigation } from "@/test/navigation-mock";
import { DEMO, callsTo, mockApi, renderWithSession } from "@/test/render";
import type { VersionVulnerabilities } from "@/lib/types";
import { ScanStatus } from "@/components/products/scan-status";
import { ApiKeys } from "./api-keys";

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("sonner", () => ({ toast }));

const DONE: VersionVulnerabilities = { status: "DONE", finishedAt: "2026-11-16T10:00:00Z", error: null, componentsScanned: 3, componentsNotScanned: 0, items: [] };

describe("write errors", () => {
  beforeEach(() => {
    resetNavigation();
    toast.success.mockReset();
    toast.error.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("rescanFailureIsShown", async () => {
    mockApi([{ method: "POST", path: "/api/products/p1/versions/v1/scan", status: 404, body: { status: 404, detail: "Version has no SBOM" } }]);
    renderWithSession(<ScanStatus productId="p1" versionId="v1" data={DONE} />);

    await userEvent.click(screen.getByRole("button", { name: /rescan/i }));

    await vi.waitFor(() => expect(toast.error).toHaveBeenCalledWith("Version has no SBOM"));
  });

  it("revokeFailureIsShownInTheDialog", async () => {
    mockApi([
      { path: "/api/api-keys", body: [{ id: "k1", name: "CI", prefix: "abcdefgh", createdAt: "2026-11-16T10:00:00Z", lastUsedAt: null, revokedAt: null }] },
      { method: "DELETE", path: "/api/api-keys/k1", status: 404, body: { status: 404, detail: "API key not found" } },
    ]);
    renderWithSession(<ApiKeys />);

    await userEvent.click(await screen.findByRole("button", { name: /^revoke$/i }));
    await userEvent.click(await screen.findByRole("button", { name: /revoke key/i }));

    expect(await screen.findByText("API key not found")).toBeInTheDocument();
  });

  it("apiKeysAreNotRequestedWhileTheSessionLoadsOrForTheDemo", async () => {
    const spy = mockApi([{ path: "/api/api-keys", body: [] }]);
    const { unmount } = renderWithSession(<ApiKeys />, null);
    unmount();
    renderWithSession(<ApiKeys />, DEMO);

    expect(await screen.findByText(/not available in the demo/i)).toBeInTheDocument();
    expect(callsTo(spy, "GET", "/api/api-keys")).toHaveLength(0);
  });
});
