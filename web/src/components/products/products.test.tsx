import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { resetNavigation } from "@/test/navigation-mock";
import { callsTo, mockApi, renderWithSession } from "@/test/render";
import type { Product, Vulnerability } from "@/lib/types";
import { ProductForm } from "./product-form";
import { DeleteProductButton } from "./delete-product-button";
import { SbomDropzone } from "./sbom-dropzone";
import { VulnerabilitiesTable } from "./vulnerabilities-table";

const LOCK: Product = { id: "p1", name: "Smart Lock", description: "Door lock", productClass: "IMPORTANT_CLASS_I", rowVersion: 3 };

// Arma una vulnerabilidad de prueba.
function vuln(id: string, kev: boolean): Vulnerability {
  return { id, aliases: [], summary: `${id} summary`, severity: "HIGH", kev, kevDateAdded: kev ? "2021-12-10" : null, componentName: "lib", componentVersion: "1.0", purl: "pkg:npm/lib@1.0" };
}

describe("products", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("staleRowVersionShowsConflictMessage", async () => {
    const spy = mockApi([{ method: "PUT", path: "/api/products/p1", status: 409, body: { status: 409, detail: "Product was modified by someone else" } }]);
    renderWithSession(<ProductForm product={LOCK} onSaved={() => {}} />);

    await userEvent.clear(screen.getByLabelText(/^name/i));
    await userEvent.type(screen.getByLabelText(/^name/i), "Smart Lock Pro");
    await userEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(await screen.findByText(/someone else changed this product/i)).toBeInTheDocument();
    expect(callsTo(spy, "PUT", "/api/products/p1")[0].json).toMatchObject({ name: "Smart Lock Pro", rowVersion: 3, productClass: "IMPORTANT_CLASS_I" });
  });

  it("deleteWithVersionsShowsReason", async () => {
    mockApi([{ method: "DELETE", path: "/api/products/p1", status: 409, body: { status: 409, detail: "Product p1 has versions and cannot be deleted" } }]);
    renderWithSession(<DeleteProductButton product={LOCK} />);

    await userEvent.click(screen.getByRole("button", { name: /delete/i }));
    await userEvent.click(await screen.findByRole("button", { name: /delete product/i }));

    expect(await screen.findByText(/has versions and can.t be deleted/i)).toBeInTheDocument();
  });

  it("rejectsFilesOver5MbWithoutUploading", async () => {
    const spy = mockApi([]);
    renderWithSession(<SbomDropzone productId="p1" versionId="v1" />);
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "big.cdx.json", { type: "application/json" });

    await userEvent.upload(screen.getByLabelText(/sbom file/i), big);

    expect(await screen.findByText(/larger than 5 mb/i)).toBeInTheDocument();
    expect(spy).not.toHaveBeenCalled();
  });

  it("showsSbomValidationErrors", async () => {
    mockApi([{ method: "PUT", path: "/api/products/p1/versions/v1/sbom", status: 400, body: { status: 400, detail: "Not a valid CycloneDX JSON SBOM", errors: { bomFormat: "must be CycloneDX" } } }]);
    renderWithSession(<SbomDropzone productId="p1" versionId="v1" />);

    await userEvent.upload(screen.getByLabelText(/sbom file/i), new File(['{"spdxVersion":"SPDX-2.3"}'], "x.json", { type: "application/json" }));

    expect(await screen.findByText(/bomFormat/)).toBeInTheDocument();
    expect(screen.getByText(/must be CycloneDX/)).toBeInTheDocument();
  });

  it("uploadsAsCycloneDxContentType", async () => {
    const spy = mockApi([{ method: "PUT", path: "/api/products/p1/versions/v1/sbom", status: 201, body: { id: "s1", componentCount: 1 } }]);
    renderWithSession(<SbomDropzone productId="p1" versionId="v1" />);

    await userEvent.upload(screen.getByLabelText(/sbom file/i), new File(['{"bomFormat":"CycloneDX"}'], "ok.cdx.json", { type: "application/json" }));

    await vi.waitFor(() => expect(callsTo(spy, "PUT", "/api/products/p1/versions/v1/sbom")).toHaveLength(1));
    const call = callsTo(spy, "PUT", "/api/products/p1/versions/v1/sbom")[0];
    expect(new Headers(call.init.headers).get("Content-Type")).toBe("application/vnd.cyclonedx+json");
  });

  it("kevRowsFirstWithBadge", () => {
    renderWithSession(<VulnerabilitiesTable items={[vuln("GHSA-low", false), vuln("GHSA-kev", true)]} />);

    const rows = screen.getAllByRole("row").slice(1);
    expect(rows[0]).toHaveTextContent("GHSA-kev");
    expect(rows[0]).toHaveTextContent(/actively exploited/i);
    expect(rows[1]).not.toHaveTextContent(/actively exploited/i);
  });
});
