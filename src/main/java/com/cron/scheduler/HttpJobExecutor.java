package com.cron.scheduler;

import com.cron.exception.InvalidJobPayloadException;
import com.cron.model.CronJob;
import com.cron.model.JobType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Calls an outbound HTTP endpoint. The RestClient carries explicit timeouts
 * (see SchedulerConfig) because a hung endpoint would otherwise occupy a
 * scheduling thread indefinitely.
 */
@Component
public class HttpJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpJobExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpJobExecutor(RestClient jobRestClient, ObjectMapper objectMapper) {
        this.restClient = jobRestClient;
        this.objectMapper = objectMapper;
    }

    public record HttpPayload(String url, String method, Map<String, String> headers, JsonNode body) {

        HttpMethod resolvedMethod() {
            return method == null || method.isBlank() ? HttpMethod.POST : HttpMethod.valueOf(method.toUpperCase());
        }
    }

    @Override
    public JobType type() {
        return JobType.HTTP;
    }

    @Override
    public void validate(JsonNode payload) {
        HttpPayload http = parse(payload);
        if (http.url() == null || http.url().isBlank()) {
            throw new InvalidJobPayloadException(type(), "'url' is required");
        }
        try {
            URI uri = new URI(http.url());
            if (!uri.isAbsolute()) {
                throw new InvalidJobPayloadException(type(), "'url' must be absolute");
            }
        } catch (URISyntaxException e) {
            throw new InvalidJobPayloadException(type(), "'url' is not a valid URI");
        }
        try {
            http.resolvedMethod();
        } catch (IllegalArgumentException e) {
            throw new InvalidJobPayloadException(type(), "unsupported method " + http.method());
        }
    }

    @Override
    public void execute(CronJob job) {
        HttpPayload http = parse(job.getPayload());

        RestClient.RequestBodySpec request = restClient
                .method(http.resolvedMethod())
                .uri(http.url());

        if (http.headers() != null) {
            http.headers().forEach(request::header);
        }
        if (http.body() != null && !http.body().isNull()) {
            request.body(http.body());
        }

        String status = request.retrieve()
                .toBodilessEntity()
                .getStatusCode()
                .toString();

        log.info("Job id={} name={} called {} {} -> {}",
                job.getId(), job.getName(), http.resolvedMethod(), http.url(), status);
    }

    private HttpPayload parse(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new InvalidJobPayloadException(type(), "payload is required");
        }
        try {
            return objectMapper.treeToValue(payload, HttpPayload.class);
        } catch (Exception e) {
            throw new InvalidJobPayloadException(type(), e.getMessage());
        }
    }
}
