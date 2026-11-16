package io.cradesk.api.reporting;

import java.util.UUID;

import io.cradesk.api.common.NotFoundException;

public class EventNotFoundException extends NotFoundException {

    // Crea el error de evento reportable inexistente o de otra empresa.
    public EventNotFoundException(UUID id) {
        super("Reportable event " + id + " not found");
    }
}
