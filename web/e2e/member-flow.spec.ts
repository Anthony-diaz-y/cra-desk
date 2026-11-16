import { readFileSync } from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";

const API = process.env.E2E_API_URL ?? "http://localhost:8081";
const ADMIN_TOKEN = process.env.CRADESK_ADMIN_TOKEN ?? "";
const SBOM = path.resolve(__dirname, "../../scripts/sample-sbom.cdx.json");

test("an invited manufacturer reports Log4Shell end to end", async ({ page, request }) => {
  test.skip(!ADMIN_TOKEN, "CRADESK_ADMIN_TOKEN is required to create the invitation");
  const run = Date.now();
  const email = `maker-${run}@example.com`;

  const invitation = await request.post(`${API}/api/admin/invitations`, {
    headers: { "X-Admin-Token": ADMIN_TOKEN },
    data: { email },
  });
  expect(invitation.status()).toBe(201);
  const { code } = (await invitation.json()) as { code: string };

  await page.goto(`/invite?code=${encodeURIComponent(code)}`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Company name").fill(`Maker ${run} GmbH`);
  await page.getByLabel("Password").fill("a-long-enough-password");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/app$/);

  await page.getByRole("link", { name: "Products" }).click();
  await page.getByRole("button", { name: "New product" }).first().click();
  await page.getByLabel("Name", { exact: true }).fill("Smart Lock");
  await page.getByRole("button", { name: "Create product" }).click();
  await page.getByRole("link", { name: /Smart Lock/ }).click();
  await page.getByRole("button", { name: "New version" }).click();
  await page.getByLabel("Version", { exact: true }).fill("1.0.0");
  await page.getByRole("button", { name: "Create version" }).click();
  await page.getByRole("link", { name: /1\.0\.0/ }).click();

  await page.getByLabel("SBOM file").setInputFiles({ name: "sbom.cdx.json", mimeType: "application/json", buffer: readFileSync(SBOM) });
  await expect(page.getByText(/Actively exploited/).first()).toBeVisible({ timeout: 120_000 });

  await page.getByRole("link", { name: /Findings/ }).click();
  await expect(page.getByText("CVE-2021-44228")).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "Confirm" }).click();
  await expect(page).toHaveURL(/\/app\/events\//);

  await page.getByRole("button", { name: "Use template" }).click();
  const measures = page.getByLabel("Immediate measures");
  await expect(measures).not.toHaveValue("");
  await measures.fill("Remote access disabled by default; firmware 1.0.1 with log4j 2.17.1 is rolling out.");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Edited by you")).toBeVisible();
  await page.getByRole("button", { name: "Approve" }).click();

  await page.getByRole("button", { name: "Mark as sent" }).click();
  await page.getByLabel("ENISA reference (optional)").fill(`ENISA-${run}`);
  await page.getByRole("button", { name: "Confirm sent" }).click();
  await expect(page.getByText(`ENISA-${run}`)).toBeVisible();
  await expect(page.getByText("Sent on time").first()).toBeVisible();

  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Markdown" }).click();
  expect((await download).suggestedFilename()).toMatch(/early_warning\.md$/);

  await page.getByRole("link", { name: "Audit log" }).click();
  await page.getByRole("button", { name: "Verify chain" }).click();
  await expect(page.getByText(/Chain intact/)).toBeVisible();
});
