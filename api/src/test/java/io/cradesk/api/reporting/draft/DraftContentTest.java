package io.cradesk.api.reporting.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

import io.cradesk.api.reporting.ReportType;
import tools.jackson.databind.json.JsonMapper;

class DraftContentTest {

    final JsonMapper json = JsonMapper.builder().build();

    // Comprueba que un JSON con todos los campos del tipo se lee y se vuelve a escribir igual.
    @Test
    void parsesAndWritesAllFields() {
        DraftContent content = DraftContent.parse(ReportType.EARLY_WARNING, DraftTestData.earlyWarningJson(), json);

        assertThat(content.fields()).containsKeys(DraftContent.fieldsOf(ReportType.EARLY_WARNING).toArray(String[]::new));
        assertThat(content.fields().get("immediateMeasures")).isEqualTo("Disable JNDI lookups");
        assertThat(DraftContent.parse(ReportType.EARLY_WARNING, content.toJson(json), json)).isEqualTo(content);
    }

    // Comprueba que faltan campos, sobran campos o un valor no es texto se informan por campo.
    @Test
    void reportsFieldErrors() {
        InvalidDraftException e = catchThrowableOfType(InvalidDraftException.class, () -> DraftContent.parse(
                ReportType.EARLY_WARNING, "{\"summary\":42,\"surprise\":\"x\"}", json));

        assertThat(e.errors())
                .containsEntry("summary", "must be text")
                .containsEntry("surprise", "is not a field of EARLY_WARNING")
                .containsEntry("immediateMeasures", "is required");
    }

    // Comprueba que un valor de más de 4000 caracteres se rechaza.
    @Test
    void rejectsTooLongValues() {
        String json = DraftTestData.earlyWarningJson().replace("Disable JNDI lookups", "x".repeat(4001));

        InvalidDraftException e = catchThrowableOfType(InvalidDraftException.class,
                () -> DraftContent.parse(ReportType.EARLY_WARNING, json, this.json));

        assertThat(e.errors()).containsEntry("immediateMeasures", "must be at most 4000 characters");
    }

    // Comprueba que un texto que no es un objeto JSON se rechaza.
    @Test
    void rejectsNonObject() {
        InvalidDraftException e = catchThrowableOfType(InvalidDraftException.class,
                () -> DraftContent.parse(ReportType.FINAL_REPORT, "not json", json));

        assertThat(e.errors()).containsKey("content");
    }
}
