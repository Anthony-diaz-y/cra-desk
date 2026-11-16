import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { CountdownDemo } from "./countdown-demo";

describe("CountdownDemo", () => {
  afterEach(() => vi.useRealTimers());

  it("countdownTicksDown", () => {
    vi.useFakeTimers();
    render(<CountdownDemo />);

    expect(screen.getByRole("timer")).toHaveTextContent("23:59:59");
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByRole("timer")).toHaveTextContent("23:59:58");
  });
});
