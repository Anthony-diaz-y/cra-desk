package io.cradesk.api.reporting;

import java.util.UUID;

import io.cradesk.api.common.ConflictException;

public class FindingNotReviewableException extends ConflictException {

    // Crea el error de hallazgo que ya fue confirmado o descartado.
    public FindingNotReviewableException(UUID id) {
        super("Finding " + id + " was already reviewed");
    }
}
