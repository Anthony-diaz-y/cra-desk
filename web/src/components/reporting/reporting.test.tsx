import "@/test/navigation-mock";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { resetNavigation } from "@/test/navigation-mock";
import { DEMO, callsTo, mockApi, renderWithSession } from "@/test/render";
import type { AiStatus, Report } from "@/lib/types";
import { DeadlineClock } from "./deadline-clock";
import { ReportEditor } from "./report-editor";
import { DismissDialog } from "./dismiss-dialog";

const AI_ON: AiStatus = { available: true, provider: "OPENAI_COMPATIBLE", model: "deepseek-chat", dailyLimit: 20, remainingToday: 19 };
const AI_OFF: AiStatus = { available: false, provider: "TEMPLATE", model: "", dailyLimit: 20, remainingToday: 20 };
const REPORT_URL = "/api/events/e1/reports/EARLY_WARNING";

// Arma un aviso temprano de prueba con los cambios indicados.
function report(changes: Partial<Report> = {}): Report {
  return {
    type: "EARLY_WARNING",
    dueAt: "2026-11-17T10:00:00Z",
    status: "PENDING",
    draftStatus: "DRAFTED",
    content: {
      summary: "Log4Shell affects Smart Lock 1.0.0.",
      activelyExploited: "Yes, listed in CISA KEV.",
      suspectedMaliciousAct: "TO BE COMPLETED",
      memberStatesAffected: "TO BE COMPLETED",
      immediateMeasures: "Disable JNDI lookups.",
    },
    generatedBy: "TEMPLATE",
    aiError: null,
    approvedAt: null,
    sentAt: null,
    enisaReference: null,
    ...changes,
  };
}

describe("DeadlineClock", () => {
  afterEach(() => vi.useRealTimers());

  it("clockTurnsOverdueAtZero", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-16T10:00:00Z"));
    render(<DeadlineClock dueAt="2026-11-16T10:00:02Z" startAt="2026-11-15T10:00:02Z" status="DUE_SOON" sentAt={null} />);

    expect(screen.getByRole("timer")).toHaveTextContent("0h 0m 2s");
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByRole("timer")).not.toHaveTextContent("-");
  });

  it("sentClockStops", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-16T10:00:00Z"));
    render(<DeadlineClock dueAt="2026-11-16T12:00:00Z" startAt="2026-11-15T12:00:00Z" status="SENT_ON_TIME" sentAt="2026-11-16T08:00:00Z" />);

    expect(screen.getByText("Sent on time")).toBeInTheDocument();
    expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  });
});

describe("ReportEditor", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("aiButtonHiddenWhenUnavailable", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_OFF} />);

    expect(screen.queryByRole("button", { name: /generate with ai/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /use template/i })).toBeInTheDocument();
  });

  it("aiButtonShowsRemaining", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />);

    expect(screen.getByRole("button", { name: /generate with ai \(19 left today\)/i })).toBeInTheDocument();
  });

  it("showsAiErrorNotice", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report({ aiError: "AI provider: HTTP 401" })} ai={AI_ON} />);

    expect(screen.getByText(/AI provider: HTTP 401/)).toBeInTheDocument();
  });

  it("highlightsToBeCompleted", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />);

    expect(screen.getAllByText(/needs your input/i)).toHaveLength(2);
  });

  it("saveShowsFieldErrors", async () => {
    mockApi([{ method: "PUT", path: REPORT_URL, status: 400, body: { status: 400, detail: "Invalid draft", errors: { summary: "is required" } } }]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />);

    await userEvent.clear(screen.getByLabelText("Summary"));
    await userEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(await screen.findByText("is required")).toBeInTheDocument();
  });

  it("savesEditedContent", async () => {
    const spy = mockApi([{ method: "PUT", path: REPORT_URL, body: report({ generatedBy: "MANUAL" }) }]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />);

    await userEvent.type(screen.getByLabelText("Immediate measures"), " Patch shipped.");
    await userEvent.click(screen.getByRole("button", { name: /save/i }));

    await vi.waitFor(() => expect(callsTo(spy, "PUT", REPORT_URL)).toHaveLength(1));
    expect(callsTo(spy, "PUT", REPORT_URL)[0].json.content.immediateMeasures).toBe("Disable JNDI lookups. Patch shipped.");
  });

  it("limitReachedSuggestsTemplate", async () => {
    mockApi([{ method: "POST", path: `${REPORT_URL}/draft?ai=true`, status: 429, body: { status: 429, detail: "Daily AI draft limit reached (20)" } }]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />);

    await userEvent.click(screen.getByRole("button", { name: /generate with ai/i }));
    await userEvent.click(await screen.findByRole("button", { name: /replace draft/i }));

    expect(await screen.findByText(/daily ai limit reached/i)).toBeInTheDocument();
    expect(screen.getByText(/use the template/i)).toBeInTheDocument();
  });

  it("sentReportIsReadOnly", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report({ draftStatus: "APPROVED", status: "SENT_ON_TIME", sentAt: "2026-11-16T09:00:00Z", enisaReference: "ENISA-1" })} ai={AI_ON} />);

    expect(screen.getByLabelText("Summary")).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /use template/i })).not.toBeInTheDocument();
    expect(screen.getByText(/ENISA-1/)).toBeInTheDocument();
  });

  it("demoCannotEdit", () => {
    mockApi([]);
    renderWithSession(<ReportEditor eventId="e1" report={report()} ai={AI_ON} />, DEMO);

    expect(screen.getByLabelText("Summary")).toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: /use template/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /approve/i })).toBeDisabled();
  });
});

describe("DismissDialog", () => {
  beforeEach(() => resetNavigation());
  afterEach(() => vi.unstubAllGlobals());

  it("dismissRequiresTenChars", async () => {
    const spy = mockApi([]);
    renderWithSession(<DismissDialog findingId="f1" cveId="CVE-2021-44228" />);

    await userEvent.click(screen.getByRole("button", { name: /dismiss/i }));
    await userEvent.type(await screen.findByLabelText(/reason/i), "too short");
    await userEvent.click(screen.getByRole("button", { name: /dismiss finding/i }));

    expect(await screen.findByText(/at least 10 characters/i)).toBeInTheDocument();
    expect(callsTo(spy, "POST", "/api/findings/f1/dismiss")).toHaveLength(0);
  });
});
