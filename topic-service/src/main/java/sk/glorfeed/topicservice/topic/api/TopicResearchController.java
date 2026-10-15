package sk.glorfeed.topicservice.topic.api;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sk.glorfeed.topicservice.topic.api.dto.ResearchReportDto;
import sk.glorfeed.topicservice.topic.research.ManualResearchGateway;

@RestController
@RequestMapping("/api/topics")
public class TopicResearchController {

    private final ManualResearchGateway researchGateway;

    public TopicResearchController(ManualResearchGateway researchGateway) {
        this.researchGateway = researchGateway;
    }

    @Operation(summary = "Research an owned topic", description = "Runs manual web research for an owned topic; may incur API charges.",
            responses = @ApiResponse(responseCode = "200", description = "Research report completed"))
    @PostMapping("/{topicId}/research")
    public ResearchReportDto runResearch(@PathVariable UUID topicId, @AuthenticationPrincipal Jwt jwt) {
        return researchGateway.run(topicId, jwt.getSubject());
    }
}
