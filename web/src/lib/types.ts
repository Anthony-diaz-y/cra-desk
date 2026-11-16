export type Role = "MEMBER" | "DEMO" | "CLI" | "PLATFORM_ADMIN";

export type Me = {
  userId: string;
  email: string | null;
  organizationId: string;
  organizationName: string;
  role: Role;
};

export type ProductClass = "DEFAULT" | "IMPORTANT_CLASS_I" | "IMPORTANT_CLASS_II" | "CRITICAL";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  productClass: ProductClass;
  rowVersion: number;
  createdAt?: string;
  updatedAt?: string;
};

export type Version = {
  id: string;
  productId: string;
  version: string;
  releasedAt: string | null;
  createdAt?: string;
};

export type UploadChannel = "WEB" | "CLI";

export type SbomSummary = {
  id: string;
  productId: string;
  versionId: string;
  specVersion: string;
  serialNumber: string | null;
  bomVersion: number | null;
  componentCount: number;
  componentsWithoutPurl: number;
  sha256: string;
  sizeBytes: number;
  uploadedVia: UploadChannel;
  uploadedAt: string;
};

export type Component = {
  type: string | null;
  group: string | null;
  name: string;
  version: string | null;
  purl: string | null;
  bomRef: string | null;
};

export type ComponentPage = {
  items: Component[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type ScanStatus = "PENDING" | "RUNNING" | "DONE" | "FAILED";

export type Vulnerability = {
  id: string;
  aliases: string[];
  summary: string | null;
  severity: string | null;
  kev: boolean;
  kevDateAdded: string | null;
  componentName: string;
  componentVersion: string | null;
  purl: string;
};

export type VersionVulnerabilities = {
  status: ScanStatus | null;
  finishedAt: string | null;
  error: string | null;
  componentsScanned: number | null;
  componentsNotScanned: number | null;
  items: Vulnerability[];
};

export type FindingStatus = "PENDING_REVIEW" | "CONFIRMED" | "DISMISSED";

export type Finding = {
  id: string;
  productId: string;
  productName: string;
  versionId: string;
  version: string;
  vulnerabilityId: string;
  cveId: string;
  summary: string | null;
  severity: string | null;
  kevDateAdded: string | null;
  present: boolean;
  status: FindingStatus;
  createdAt: string;
};

export type ReportType = "EARLY_WARNING" | "NOTIFICATION" | "FINAL_REPORT";
export type DeadlineStatus = "PENDING" | "DUE_SOON" | "OVERDUE" | "SENT_ON_TIME" | "SENT_LATE";
export type DraftStatus = "EMPTY" | "DRAFTED" | "APPROVED";

export type ReportSummary = {
  type: ReportType;
  dueAt: string;
  status: DeadlineStatus;
  draftStatus: DraftStatus;
  sentAt: string | null;
  enisaReference: string | null;
};

export type EventResponse = {
  id: string;
  findingId: string;
  productId: string;
  productName: string;
  versionId: string;
  version: string;
  cveId: string;
  confirmedAt: string;
  reports: ReportSummary[];
};

export type Report = {
  type: ReportType;
  dueAt: string;
  status: DeadlineStatus;
  draftStatus: DraftStatus;
  content: Record<string, string> | null;
  generatedBy: string | null;
  aiError: string | null;
  approvedAt: string | null;
  sentAt: string | null;
  enisaReference: string | null;
};

export type AiStatus = {
  available: boolean;
  provider: string;
  model: string;
  dailyLimit: number;
  remainingToday: number;
};

export type AuditEntry = {
  seq: number;
  occurredAt: string;
  actorId: string | null;
  actorRole: string;
  action: string;
  targetType: string;
  targetId: string;
  details: Record<string, unknown>;
  prevHash: string;
  hash: string;
};

export type AuditPage = {
  items: AuditEntry[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type AuditVerification = {
  valid: boolean;
  entries: number;
  firstInvalidSeq: number | null;
};

export type ApiKey = {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
};

export type ApiKeyCreated = {
  id: string;
  name: string;
  prefix: string;
  key: string;
  createdAt: string;
};

export type Problem = {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
};
