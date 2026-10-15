package sk.glorfeed.researchservice.research.api;

import java.net.InetAddress;
import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.services.ResearchService;

@RestController
@RequestMapping("/api/research/topics")
@ConditionalOnProperty(prefix = "research.ai", name = "manual-enabled", havingValue = "true")
@Tag(name = "Research", description = "Manually investigate one projected topic.")
public class ResearchController {

    private static final Logger log = LoggerFactory.getLogger(ResearchController.class);

    private final ResearchService researchService;

    public ResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    @Operation(summary = "Research one topic", description = "Calls web search and returns a cited report. Each request may incur API charges.")
    @PostMapping("/{topicId}/run")
    public ResearchReport run(@PathVariable UUID topicId, HttpServletRequest request) {
        log.info("Received manual research request: topicId={}, remoteAddress={}", topicId, request.getRemoteAddr());
        if (!isLoopback(request.getRemoteAddr()) || !isSameLocalOrigin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manual research is available only from localhost");
        }
        return researchService.researchTopic(topicId);
    }

    private boolean isSameLocalOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        if (origin.isBlank() || "null".equals(origin)) {
            return false;
        }

        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            String originHost = unbracket(uri.getHost());
            String requestHost = unbracket(request.getServerName());
            if (scheme == null || originHost == null || requestHost == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !scheme.equalsIgnoreCase(request.getScheme())
                    || !originHost.equalsIgnoreCase(requestHost)
                    || !isLoopbackHost(originHost)) {
                return false;
            }

            int originPort = uri.getPort();
            if (originPort < 0) {
                originPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }
            return originPort == request.getServerPort();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        return isLoopback(host);
    }

    private String unbracket(String host) {
        if (host != null && host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            return InetAddress.ofLiteral(remoteAddress).isLoopbackAddress();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
