package io.cradesk.api.identity;

import io.cradesk.api.common.NotFoundException;

public class DemoDisabledException extends NotFoundException {

    // Crea el error de demo desactivada o sin cuenta demo creada.
    public DemoDisabledException() {
        super("The demo is not available");
    }
}
