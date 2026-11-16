package io.cradesk.api.reporting;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private ReportableEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReportType type;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_status", nullable = false, length = 10)
    private DraftStatus draftStatus;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String content;

    @Column(name = "generated_by", length = 20)
    private String generatedBy;

    @Column(name = "ai_error", length = 300)
    private String aiError;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by")
    private UUID sentBy;

    @Column(name = "enisa_reference", length = 100)
    private String enisaReference;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructor vacío que JPA necesita para cargar reportes desde la base de datos.
    protected Report() {
    }

    // Crea un reporte vacío del evento con su vencimiento según el tipo.
    public Report(ReportableEvent event, ReportType type, Instant now) {
        this.event = event;
        this.type = type;
        this.dueAt = type.dueAt(event.getConfirmedAt());
        this.draftStatus = DraftStatus.EMPTY;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Indica si el reporte ya se marcó enviado a ENISA.
    public boolean isSent() {
        return sentAt != null;
    }

    // Guarda un borrador nuevo hecho por ese generador, sin aprobar.
    public void draft(String contentJson, String generator, String error, Instant now) {
        content = contentJson;
        generatedBy = generator;
        aiError = error;
        unapprove(now);
    }

    // Reemplaza el contenido por el que editó una persona y quita la aprobación.
    public void edit(String contentJson, Instant now) {
        content = contentJson;
        unapprove(now);
    }

    // Aprueba el borrador actual con quién y cuándo.
    public void approve(UUID userId, Instant now) {
        draftStatus = DraftStatus.APPROVED;
        approvedBy = userId;
        approvedAt = now.truncatedTo(ChronoUnit.MICROS);
        updatedAt = approvedAt;
    }

    // Marca el reporte como enviado a ENISA, lo que detiene su reloj.
    public void markSent(UUID userId, Instant sent, String reference, Instant now) {
        sentAt = sent.truncatedTo(ChronoUnit.MICROS);
        sentBy = userId;
        enisaReference = reference;
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Deja el reporte como borrador sin aprobar y actualiza la fecha de cambio.
    private void unapprove(Instant now) {
        draftStatus = DraftStatus.DRAFTED;
        approvedBy = null;
        approvedAt = null;
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Calcula el estado del plazo del reporte en ese instante.
    public DeadlineStatus statusAt(Instant now) {
        return Deadlines.status(event.getConfirmedAt(), dueAt, sentAt, now);
    }

    // Devuelve el id del reporte.
    public UUID getId() {
        return id;
    }

    // Devuelve el evento al que pertenece el reporte.
    public ReportableEvent getEvent() {
        return event;
    }

    // Devuelve el tipo de reporte.
    public ReportType getType() {
        return type;
    }

    // Devuelve el vencimiento del reporte.
    public Instant getDueAt() {
        return dueAt;
    }

    // Devuelve el estado del borrador.
    public DraftStatus getDraftStatus() {
        return draftStatus;
    }

    // Devuelve el contenido del borrador en JSON, o null si está vacío.
    public String getContent() {
        return content;
    }

    // Devuelve quién generó el borrador: TEMPLATE, el proveedor de IA o MANUAL.
    public String getGeneratedBy() {
        return generatedBy;
    }

    // Devuelve el motivo corto por el que falló la IA, o null.
    public String getAiError() {
        return aiError;
    }

    // Devuelve quién aprobó el borrador, o null.
    public UUID getApprovedBy() {
        return approvedBy;
    }

    // Devuelve cuándo se aprobó el borrador, o null.
    public Instant getApprovedAt() {
        return approvedAt;
    }

    // Devuelve cuándo se envió a ENISA, o null si no se envió.
    public Instant getSentAt() {
        return sentAt;
    }

    // Devuelve quién marcó el reporte como enviado, o null.
    public UUID getSentBy() {
        return sentBy;
    }

    // Devuelve la referencia que dio ENISA, o null.
    public String getEnisaReference() {
        return enisaReference;
    }

    // Devuelve cuándo se modificó el reporte por última vez.
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
