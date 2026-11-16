import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RequestAccessForm } from "./request-access-form";

// Reemplaza fetch por uno que siempre contesta esa respuesta y devuelve el espía.
function mockFetch(status: number, body?: unknown) {
  const spy = vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", spy);
  document.cookie = "XSRF-TOKEN=t; path=/";
  return spy;
}

describe("RequestAccessForm", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("submitsEmailAndShowsThanks", async () => {
    const spy = mockFetch(202);
    render(<RequestAccessForm />);

    await userEvent.type(screen.getByLabelText(/work email/i), "cto@lockmaker.eu");
    await userEvent.type(screen.getByLabelText(/company/i), "Lockmaker");
    await userEvent.click(screen.getByRole("button", { name: /request access/i }));

    expect(await screen.findByText(/thanks/i)).toBeInTheDocument();
    const [url, init] = spy.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/access-requests");
    expect(JSON.parse(init.body as string)).toEqual({ email: "cto@lockmaker.eu", company: "Lockmaker", message: "" });
  });

  it("showsFieldErrorFromApi", async () => {
    mockFetch(400, { status: 400, detail: "Invalid request", errors: { email: "must be a well-formed email address" } });
    render(<RequestAccessForm />);

    await userEvent.type(screen.getByLabelText(/work email/i), "nope");
    await userEvent.click(screen.getByRole("button", { name: /request access/i }));

    expect(await screen.findByText("must be a well-formed email address")).toBeInTheDocument();
  });

  it("requiresEmail", async () => {
    const spy = mockFetch(202);
    render(<RequestAccessForm />);

    await userEvent.click(screen.getByRole("button", { name: /request access/i }));

    expect(await screen.findByText(/enter your work email/i)).toBeInTheDocument();
    expect(spy).not.toHaveBeenCalled();
  });
});
