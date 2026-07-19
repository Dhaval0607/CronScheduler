package com.cron.executor;

import com.cron.exception.InvalidJobPayloadException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payloads are validated at create/update time, so these cases are what stands
 * between a typo and a job that fails silently at 3am.
 */
class PayloadValidationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final EmailJobExecutor email = new EmailJobExecutor(objectMapper);
    private final HttpJobExecutor http = new HttpJobExecutor(null, objectMapper);

    private JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    @Test
    void acceptsAWellFormedEmailPayload() {
        assertThatCode(() -> email.validate(json("""
                {"to":"a@b.com","subject":"hi","body":"there"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmailWithoutRecipient() {
        assertThatThrownBy(() -> email.validate(json("""
                {"subject":"hi"}
                """)))
                .isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("'to' is required");
    }

    @Test
    void rejectsEmailWithBlankSubject() {
        assertThatThrownBy(() -> email.validate(json("""
                {"to":"a@b.com","subject":"   "}
                """)))
                .isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("'subject' is required");
    }

    @Test
    void rejectsMissingPayloadEntirely() {
        assertThatThrownBy(() -> email.validate(null))
                .isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("payload is required");
    }

    @Test
    void acceptsAWellFormedHttpPayload() {
        assertThatCode(() -> http.validate(json("""
                {"url":"https://example.com/hook","method":"POST"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void rejectsRelativeUrl() {
        assertThatThrownBy(() -> http.validate(json("""
                {"url":"/relative/path"}
                """)))
                .isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("must be absolute");
    }

    @Test
    void rejectsUnsupportedHttpMethod() {
        assertThatThrownBy(() -> http.validate(json("""
                {"url":"https://example.com","method":"TELEPORT"}
                """)))
                .isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("unsupported method");
    }

    @Test
    void defaultsToPostWhenMethodOmitted() {
        HttpJobExecutor.HttpPayload payload =
                objectMapper.treeToValue(json("""
                        {"url":"https://example.com"}
                        """), HttpJobExecutor.HttpPayload.class);

        assertThat(payload.resolvedMethod().name()).isEqualTo("POST");
    }

    @Test
    void treatsLowercaseMethodAsValid() {
        assertThatCode(() -> http.validate(json("""
                {"url":"https://example.com","method":"get"}
                """))).doesNotThrowAnyException();
    }
}
