import type { ProductClass, ReportType } from "./types";

const REPORT_TITLES: Record<ReportType, string> = {
  EARLY_WARNING: "Early warning",
  NOTIFICATION: "Vulnerability notification",
  FINAL_REPORT: "Final report",
};

const REPORT_WINDOWS: Record<ReportType, string> = {
  EARLY_WARNING: "24 hours",
  NOTIFICATION: "72 hours",
  FINAL_REPORT: "14 days",
};

const CLASS_LABELS: Record<ProductClass, string> = {
  DEFAULT: "Default",
  IMPORTANT_CLASS_I: "Important · Class I",
  IMPORTANT_CLASS_II: "Important · Class II",
  CRITICAL: "Critical",
};

// Devuelve el título en inglés de un tipo de reporte.
export function reportTitle(type: ReportType): string {
  return REPORT_TITLES[type];
}

// Devuelve el plazo legal de un tipo de reporte.
export function reportWindow(type: ReportType): string {
  return REPORT_WINDOWS[type];
}

// Devuelve el nombre legible de una clase de producto del CRA.
export function productClassLabel(value: ProductClass): string {
  return CLASS_LABELS[value];
}

// Convierte un nombre de campo en camelCase en un título legible, como en la exportación Markdown.
export function fieldLabel(field: string): string {
  const spaced = field.replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

// Escribe una fecha y hora en el formato local del navegador.
export function dateTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : "—";
}

// Escribe solo la fecha en el formato local del navegador.
export function date(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleDateString(undefined, { dateStyle: "medium" }) : "—";
}

// Acorta un id o hash a sus primeros caracteres.
export function short(value: string | null | undefined, length = 8): string {
  return value ? value.slice(0, length) : "—";
}
