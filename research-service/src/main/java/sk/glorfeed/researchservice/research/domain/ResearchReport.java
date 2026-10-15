package sk.glorfeed.researchservice.research.domain;

import java.util.List;
import java.util.UUID;

public record ResearchReport(
        UUID topicId,
        String responseId,
        String model,
        String text,
        List<ResearchCitation> citations,
        List<ResearchSource> sources,
        long inputTokens,
        long outputTokens,
        int webSearchCalls
) {
}
