package sk.glorfeed.topicservice.topic.research;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import sk.glorfeed.topicservice.topic.api.dto.ResearchReportDto;

@Component
public class InternalResearchClient {

    private static final Logger log = LoggerFactory.getLogger(InternalResearchClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Research-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;
    private final boolean enabled;

    public InternalResearchClient(
            @Value("${research.gateway.base-url:http://research-service:8080}") String baseUrl,
            @Value("${research.gateway.internal-token:}") String internalToken,
            @Value("${research.gateway.enabled:false}") boolean enabled
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(105));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
        this.enabled = enabled;
    }

    public ResearchReportDto run(UUID topicId) {
        if (!enabled || internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Manual research is not enabled");
        }

        log.info("Calling private research service for topicId={}", topicId);
        try {
            ResearchReportDto report = restClient.post()
                    .uri("/internal/research/topics/{topicId}/run", topicId)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .retrieve()
                    .body(ResearchReportDto.class);
            if (report == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Research service returned no report");
            }
            if (!topicId.equals(report.topicId())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Research service returned a mismatched topic");
            }
            return report;
        } catch (RestClientResponseException exception) {
            throw mapResponseError(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Research service request failed");
        }
    }

    private ResponseStatusException mapResponseError(int status) {
        return switch (status) {
            case 404 -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "The research topic projection is not ready; try again shortly");
            case 409 -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "Research for this topic is already running");
            case 422 -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The configured OpenAI API key was rejected");
            case 503 -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Manual research is unavailable; check its server configuration");
            case 504 -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Research request timed out");
            default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Research request failed");
        };
    }
}
