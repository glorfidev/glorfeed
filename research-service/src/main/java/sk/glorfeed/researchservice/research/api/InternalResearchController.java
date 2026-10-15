package sk.glorfeed.researchservice.research.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.services.ResearchService;

@RestController
@RequestMapping("/internal/research/topics")
public class InternalResearchController {

    private static final Logger log = LoggerFactory.getLogger(InternalResearchController.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Research-Internal-Token";

    private final ResearchService researchService;
    private final String internalToken;
    private final boolean enabled;

    public InternalResearchController(
            ResearchService researchService,
            @Value("${research.gateway.internal-token:}") String internalToken,
            @Value("${research.gateway.enabled:false}") boolean enabled
    ) {
        this.researchService = researchService;
        this.internalToken = internalToken;
        this.enabled = enabled;
    }

    @PostMapping("/{topicId}/run")
    public ResearchReport run(
            @PathVariable UUID topicId,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String suppliedToken
    ) {
        verifyGateway(suppliedToken);
        log.info("Received private manual research request: topicId={}", topicId);
        return researchService.researchTopic(topicId);
    }

    private void verifyGateway(String suppliedToken) {
        if (!enabled || internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Manual research is not enabled");
        }
        byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Research gateway authentication failed");
        }
    }
}
