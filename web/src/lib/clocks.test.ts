import { describe, expect, it } from "vitest";
import { formatRemaining, remainingMs, statusLabel, statusTone } from "./clocks";

describe("clocks", () => {
  it("formatsHoursMinutesSeconds", () => {
    expect(formatRemaining(86_399_000)).toBe("23h 59m 59s");
    expect(formatRemaining(61_000)).toBe("0h 1m 1s");
  });

  it("formatsDaysOver48h", () => {
    expect(formatRemaining((3 * 24 + 2) * 3_600_000 + 59_000)).toBe("3d 2h");
    expect(formatRemaining(48 * 3_600_000)).toBe("2d 0h");
  });

  it("neverNegative", () => {
    expect(formatRemaining(-5000)).toBe("0s");
    expect(formatRemaining(0)).toBe("0s");
  });

  it("remainingFromDueAt", () => {
    const now = Date.parse("2026-11-16T10:00:00Z");
    expect(remainingMs("2026-11-16T11:00:00Z", now)).toBe(3_600_000);
    expect(remainingMs("2026-11-16T09:00:00Z", now)).toBe(-3_600_000);
  });

  it("tonesPerStatus", () => {
    expect(statusTone("PENDING")).toBe("neutral");
    expect(statusTone("DUE_SOON")).toBe("warning");
    expect(statusTone("OVERDUE")).toBe("danger");
    expect(statusTone("SENT_ON_TIME")).toBe("success");
    expect(statusTone("SENT_LATE")).toBe("danger");
    expect(statusLabel("DUE_SOON")).toBe("Due soon");
    expect(statusLabel("SENT_LATE")).toBe("Sent late");
  });
});
