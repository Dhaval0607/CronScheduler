package com.cron.executor;

import com.cron.exception.InvalidJobPayloadException;
import com.cron.model.CronJob;
import com.cron.model.JobType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub delivery: validates the payload and logs what it would send.
 * Swap the body of {@link #execute} for a real JavaMailSender call once
 * SMTP credentials exist; nothing else needs to change.
 */
@Component
public class EmailJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(EmailJobExecutor.class);

    private final ObjectMapper objectMapper;

    public EmailJobExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record EmailPayload(String to, String subject, String body) {
    }

    @Override
    public JobType type() {
        return JobType.EMAIL;
    }

    @Override
    public void validate(JsonNode payload) {
        EmailPayload email = parse(payload);
        if (email.to() == null || email.to().isBlank()) {
            throw new InvalidJobPayloadException(type(), "'to' is required");
        }
        if (email.subject() == null || email.subject().isBlank()) {
            throw new InvalidJobPayloadException(type(), "'subject' is required");
        }
    }

    @Override
    public void execute(CronJob job) {
        EmailPayload email = parse(job.getPayload());
        log.info("[STUB] Would send email for job id={} name={} to={} subject={}",
                job.getId(), job.getName(), email.to(), email.subject());
    }

    private EmailPayload parse(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new InvalidJobPayloadException(type(), "payload is required");
        }
        try {
            return objectMapper.treeToValue(payload, EmailPayload.class);
        } catch (Exception e) {
            throw new InvalidJobPayloadException(type(), e.getMessage());
        }
    }
}
