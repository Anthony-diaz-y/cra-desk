package io.cradesk.api.sbom;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.sbom.SbomService.UploadResult;

@RestController
public class SbomByNameController {

    private final SbomService sboms;

    // Recibe el servicio de SBOM por inyección de dependencias.
    public SbomByNameController(SbomService sboms) {
        this.sboms = sboms;
    }

    // Sube el SBOM indicando producto y versión por nombre, como hace la CLI: 201 si es nuevo, 200 si reemplaza.
    @PutMapping("/api/sbom")
    public ResponseEntity<SbomSummary> upload(@RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "") String version, @RequestParam(defaultValue = "true") boolean createVersion,
            HttpServletRequest request) throws IOException, HttpMediaTypeNotSupportedException {
        UploadResult result = sboms.uploadByName(product, version, createVersion, request.getInputStream(),
                request.getContentType());
        return ResponseEntity.status(result.replaced() ? HttpStatus.OK : HttpStatus.CREATED).body(result.summary());
    }
}
