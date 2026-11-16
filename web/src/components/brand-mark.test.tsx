import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { BrandMark } from "./brand-mark";

describe("BrandMark", () => {
  it("isDecorativeByDefault", () => {
    const { container } = render(<BrandMark />);

    const svg = container.querySelector("svg");
    expect(svg).toHaveAttribute("aria-hidden", "true");
    expect(svg?.querySelectorAll("[data-part='tick']")).toHaveLength(4);
    expect(svg?.querySelector("[data-part='check']")).not.toBeNull();
  });

  it("hasAnAccessibleNameWhenTitled", () => {
    render(<BrandMark title="CRA Desk" />);

    expect(screen.getByRole("img", { name: "CRA Desk" })).toBeInTheDocument();
  });

  it("simpleVariantDropsTheClockTicks", () => {
    const { container } = render(<BrandMark variant="simple" />);

    expect(container.querySelectorAll("[data-part='tick']")).toHaveLength(0);
    expect(container.querySelector("[data-part='check']")).not.toBeNull();
  });
});
