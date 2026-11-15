package io.cradesk.api.sbom;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.sbom.SbomService.UploadResult;

@RestController
@RequestMapping("/api/products/{productId}/versions/{versionId}/sbom")
public class SbomController {

    private final SbomService sboms;

    // Recibe el servicio de SBOM por inyección de dependencias.
    public SbomController(SbomService sboms) {
        this.sboms = sboms;
    }

    // Sube el SBOM crudo de la versión: 201 si es nuevo, 200 si reemplaza al anterior.
    @PutMapping
    public ResponseEntity<SbomSummary> upload(@PathVariable UUID productId, @PathVariable UUID versionId,
            HttpServletRequest request) throws IOException, HttpMediaTypeNotSupportedException {
        UploadResult result = sboms.upload(productId, versionId, request.getInputStream(), request.getContentType());
        return ResponseEntity.status(result.replaced() ? HttpStatus.OK : HttpStatus.CREATED).body(result.summary());
    }
}
