package io.cradesk.api.reporting;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.reporting.ReportingDtos.DismissRequest;
import io.cradesk.api.reporting.ReportingDtos.EventResponse;
import io.cradesk.api.reporting.ReportingDtos.ReviewedFinding;

@RestController
public class ReportingController {

    private final ReviewService reviews;
    private final EventService events;

    // Recibe los servicios de revisión y de eventos por inyección de dependencias.
    public ReportingController(ReviewService reviews, EventService events) {
        this.reviews = reviews;
        this.events = events;
    }

    // Confirma un hallazgo, abre su evento reportable y responde 201 con los relojes.
    @PostMapping("/api/findings/{id}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse confirm(@PathVariable UUID id) {
        return reviews.confirm(id);
    }

    // Descarta un hallazgo con un motivo obligatorio.
    @PostMapping("/api/findings/{id}/dismiss")
    public ReviewedFinding dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request) {
        return reviews.dismiss(id, request.reason());
    }

    // Lista los eventos reportables de la empresa con sus relojes.
    @GetMapping("/api/events")
    public List<EventResponse> events() {
        return events.list();
    }

    // Devuelve un evento reportable de la empresa.
    @GetMapping("/api/events/{id}")
    public EventResponse event(@PathVariable UUID id) {
        return events.get(id);
    }
}
