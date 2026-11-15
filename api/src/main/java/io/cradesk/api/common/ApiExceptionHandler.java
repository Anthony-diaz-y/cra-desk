package io.cradesk.api.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.cradesk.api.catalog.DuplicateProductException;
import io.cradesk.api.catalog.DuplicateVersionException;
import io.cradesk.api.catalog.ProductHasVersionsException;
import io.cradesk.api.catalog.ProductNotFoundException;
import io.cradesk.api.catalog.VersionNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Responde 400 con un mapa campo → mensaje cuando falla la validación.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // Responde 404 en formato problem details.
    @ExceptionHandler({ ProductNotFoundException.class, VersionNotFoundException.class })
    ProblemDetail notFound(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler({ DuplicateProductException.class, DuplicateVersionException.class,
            ProductHasVersionsException.class })
    // Responde 409 en formato problem details.
    ProblemDetail conflict(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
