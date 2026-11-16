package io.cradesk.api.vulns;

import java.util.UUID;

import io.cradesk.api.common.NotFoundException;

public class FindingNotFoundException extends NotFoundException {

    // Crea el error de hallazgo inexistente o de otra empresa.
    public FindingNotFoundException(UUID id) {
        super("Finding " + id + " not found");
    }
}
