import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { navigation, resetNavigation, router } from "@/test/navigation-mock";
import { callsTo, mockApi } from "@/test/render";
import { LoginForm } from "./login-form";
import { InviteForm } from "./invite-form";

describe("LoginForm", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("loginSuccessGoesToNext", async () => {
    navigation.search = "next=%2Fapp%2Ffindings";
    const spy = mockApi([{ method: "POST", path: "/api/auth/login", status: 204 }]);
    render(<LoginForm />);

    await userEvent.type(screen.getByLabelText(/email/i), "maker@example.com");
    await userEvent.type(screen.getByLabelText(/password/i), "correct-horse-battery");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/app/findings"));
    expect(callsTo(spy, "POST", "/api/auth/login")[0].json).toEqual({ email: "maker@example.com", password: "correct-horse-battery" });
  });

  it("ignoresNextOutsideTheApp", async () => {
    navigation.search = "next=https%3A%2F%2Fevil.example";
    mockApi([{ method: "POST", path: "/api/auth/login", status: 204 }]);
    render(<LoginForm />);

    await userEvent.type(screen.getByLabelText(/email/i), "maker@example.com");
    await userEvent.type(screen.getByLabelText(/password/i), "correct-horse-battery");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/app"));
  });

  it("loginShowsInvalidCredentials", async () => {
    mockApi([{ method: "POST", path: "/api/auth/login", status: 401, body: { status: 401, detail: "Invalid email or password" } }]);
    render(<LoginForm />);

    await userEvent.type(screen.getByLabelText(/email/i), "maker@example.com");
    await userEvent.type(screen.getByLabelText(/password/i), "wrong-password-123");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
    expect(router.replace).not.toHaveBeenCalled();
  });
});

describe("InviteForm", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("inviteValidatesPasswordLength", async () => {
    navigation.search = "code=abc123";
    const spy = mockApi([{ method: "POST", path: "/api/auth/accept-invitation", status: 201 }]);
    render(<InviteForm />);

    expect(screen.getByLabelText(/invitation code/i)).toHaveValue("abc123");
    await userEvent.type(screen.getByLabelText(/^email/i), "maker@example.com");
    await userEvent.type(screen.getByLabelText(/company/i), "Maker GmbH");
    await userEvent.type(screen.getByLabelText(/^password/i), "short");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByText(/at least 12 characters/i)).toBeInTheDocument();
    expect(callsTo(spy, "POST", "/api/auth/accept-invitation")).toHaveLength(0);
  });

  it("acceptsInvitationAndOpensTheApp", async () => {
    navigation.search = "code=abc123";
    const spy = mockApi([{ method: "POST", path: "/api/auth/accept-invitation", status: 201 }]);
    render(<InviteForm />);

    await userEvent.type(screen.getByLabelText(/^email/i), "maker@example.com");
    await userEvent.type(screen.getByLabelText(/company/i), "Maker GmbH");
    await userEvent.type(screen.getByLabelText(/^password/i), "a-long-enough-password");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/app"));
    expect(callsTo(spy, "POST", "/api/auth/accept-invitation")[0].json).toEqual({
      code: "abc123",
      email: "maker@example.com",
      password: "a-long-enough-password",
      organizationName: "Maker GmbH",
    });
  });
});
