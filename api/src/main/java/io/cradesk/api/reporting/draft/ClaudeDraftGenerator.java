package io.cradesk.api.reporting.draft;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;

import io.cradesk.api.reporting.ReportType;
import io.cradesk.api.reporting.draft.DraftRecords.EarlyWarningDraft;
import io.cradesk.api.reporting.draft.DraftRecords.FinalReportDraft;
import io.cradesk.api.reporting.draft.DraftRecords.NotificationDraft;
import tools.jackson.databind.json.JsonMapper;

public class ClaudeDraftGenerator implements DraftGenerator {

    static final long MAX_TOKENS = 4000L;

    private final AnthropicClient client;
    private final String model;
    private final JsonMapper json;

    // Recibe el cliente oficial de la Claude API, el modelo y el mapper JSON.
    public ClaudeDraftGenerator(AnthropicClient client, String model, JsonMapper json) {
        this.client = client;
        this.model = model;
        this.json = json;
    }

    // Devuelve el nombre con el que se guarda un borrador hecho con Claude.
    @Override
    public String name() {
        return "CLAUDE";
    }

    // Pide el borrador con salida estructurada al record del tipo y lo valida contra sus campos.
    @Override
    public DraftContent generate(ReportType type, DraftContext context) {
        Object draft = switch (type) {
            case EARLY_WARNING -> ask(EarlyWarningDraft.class, type, context);
            case NOTIFICATION -> ask(NotificationDraft.class, type, context);
            case FINAL_REPORT -> ask(FinalReportDraft.class, type, context);
        };
        try {
            return DraftContent.parse(type, json.writeValueAsString(draft), json);
        } catch (InvalidDraftException e) {
            throw new DraftGenerationException("AI provider: invalid draft " + e.errors().keySet(), e);
        }
    }

    // Envía la petición a la Messages API y devuelve el record ya parseado, o lanza un error corto.
    private <T> T ask(Class<T> recordClass, ReportType type, DraftContext context) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(DraftPrompt.system(type))
                .addUserMessage(DraftPrompt.user(context))
                .outputConfig(recordClass)
                .build();
        StructuredMessage<T> message;
        try {
            message = client.messages().create(params);
        } catch (AnthropicServiceException e) {
            throw new DraftGenerationException("AI provider: HTTP " + e.statusCode(), e);
        } catch (RuntimeException e) {
            throw new DraftGenerationException("AI provider: " + e.getClass().getSimpleName(), e);
        }
        String stop = message.stopReason().map(Object::toString).orElse("").toLowerCase(java.util.Locale.ROOT);
        if (!stop.equals("end_turn")) {
            throw new DraftGenerationException("AI provider: stopped (" + stop + ")");
        }
        try {
            return message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new DraftGenerationException("AI provider: empty answer"));
        } catch (DraftGenerationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DraftGenerationException("AI provider: unreadable answer", e);
        }
    }
}
