import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { resetNavigation } from "@/test/navigation-mock";
import { callsTo, mockApi, renderWithSession } from "@/test/render";
import { VerifyChain } from "./verify-chain";
import { ApiKeys } from "@/components/settings/api-keys";

const KEY = "crd_abcdefgh_s3cr3t-value-0123456789";

describe("VerifyChain", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("showsIntactChain", async () => {
    mockApi([{ path: "/api/audit/verify", body: { valid: true, entries: 12, firstInvalidSeq: null } }]);
    renderWithSession(<VerifyChain />);

    await userEvent.click(screen.getByRole("button", { name: /verify chain/i }));

    expect(await screen.findByText(/chain intact — 12 entries/i)).toBeInTheDocument();
  });

  it("showsFirstInvalidSeq", async () => {
    mockApi([{ path: "/api/audit/verify", body: { valid: false, entries: 2, firstInvalidSeq: 3 } }]);
    renderWithSession(<VerifyChain />);

    await userEvent.click(screen.getByRole("button", { name: /verify chain/i }));

    expect(await screen.findByText(/entry #3 was altered or removed/i)).toBeInTheDocument();
  });
});

describe("ApiKeys", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("showsKeyOnceAndForgetsOnClose", async () => {
    mockApi([
      { path: "/api/api-keys", body: [] },
      { method: "POST", path: "/api/api-keys", status: 201, body: { id: "k1", name: "CI", prefix: "abcdefgh", key: KEY, createdAt: "2026-11-16T10:00:00Z" } },
    ]);
    renderWithSession(<ApiKeys />);

    await userEvent.click(await screen.findByRole("button", { name: /new key/i }));
    await userEvent.type(screen.getByLabelText(/key name/i), "CI");
    await userEvent.click(screen.getByRole("button", { name: /create key/i }));

    expect(await screen.findByText(KEY)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /i saved it/i }));
    await vi.waitFor(() => expect(screen.queryByText(KEY)).not.toBeInTheDocument());
  });

  it("revokeAsksForConfirmation", async () => {
    const spy = mockApi([
      { path: "/api/api-keys", body: [{ id: "k1", name: "CI pipeline", prefix: "abcdefgh", createdAt: "2026-11-16T10:00:00Z", lastUsedAt: null, revokedAt: null }] },
      { method: "DELETE", path: "/api/api-keys/k1", status: 204 },
    ]);
    renderWithSession(<ApiKeys />);

    await userEvent.click(await screen.findByRole("button", { name: /revoke/i }));
    expect(callsTo(spy, "DELETE", "/api/api-keys/k1")).toHaveLength(0);
    await userEvent.click(await screen.findByRole("button", { name: /revoke key/i }));

    await vi.waitFor(() => expect(callsTo(spy, "DELETE", "/api/api-keys/k1")).toHaveLength(1));
  });
});
