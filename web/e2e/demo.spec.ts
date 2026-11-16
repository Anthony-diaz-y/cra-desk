import { expect, test } from "@playwright/test";

test("a visitor explores the read-only demo", async ({ page }) => {
  const cspErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" && /Content Security Policy/i.test(message.text())) {
      cspErrors.push(message.text());
    }
  });

  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("on time");
  await page.getByRole("button", { name: "Try the demo" }).first().click();

  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByText("Read-only demo").first()).toBeVisible();
  await expect(page.getByText("CVE-2021-44228").first()).toBeVisible();
  await expect(page.getByText("Sent on time").first()).toBeVisible();

  await page.getByRole("link", { name: /CVE-2021-44228/ }).click();
  await expect(page.getByLabel("Summary")).toHaveAttribute("readonly", "");
  await page.getByRole("tab", { name: /Vulnerability notification/ }).click();
  await expect(page.getByRole("button", { name: "Approve" })).toBeDisabled();

  await page.getByRole("link", { name: "Audit log" }).click();
  await page.getByRole("button", { name: "Verify chain" }).click();
  await expect(page.getByText(/Chain intact/)).toBeVisible();

  expect(cspErrors).toEqual([]);
});
