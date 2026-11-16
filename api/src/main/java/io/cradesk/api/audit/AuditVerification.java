package io.cradesk.api.audit;

public record AuditVerification(boolean valid, long entries, Long firstInvalidSeq) {
}
