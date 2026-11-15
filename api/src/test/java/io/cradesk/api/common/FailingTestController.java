package io.cradesk.api.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FailingTestController {

    // Lanza un error inesperado para probar la respuesta 500.
    @GetMapping("/api/test-only/boom")
    String boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
