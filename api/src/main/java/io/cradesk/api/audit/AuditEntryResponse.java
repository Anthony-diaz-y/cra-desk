package io.cradesk.api.audit;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record AuditEntryResponse(long seq, Instant occurredAt, UUID actorId, String actorRole, String action,
        String targetType, String targetId, JsonNode details, String prevHash, String hash) {

    // Convierte una entrada guardada en su respuesta, con los detalles como objeto JSON.
    static AuditEntryResponse of(AuditEntry e, JsonMapper json) {
        return new AuditEntryResponse(e.getSeq(), e.getOccurredAt(), e.getActorId(), e.getActorRole(),
                e.getAction(), e.getTargetType(), e.getTargetId(), json.readTree(e.getDetails()), e.getPrevHash(),
                e.getHash());
    }
}
