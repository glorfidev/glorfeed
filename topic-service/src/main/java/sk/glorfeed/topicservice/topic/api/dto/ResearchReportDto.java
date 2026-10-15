package sk.glorfeed.topicservice.topic.api.dto;

import java.util.List;
import java.util.UUID;

public record ResearchReportDto(
    UUID topicId,
    String responseId,
    String model,
    String text,
    List<CitationDto> citations,
    List<SourceDto> sources,
    long inputTokens,
    long outputTokens,
    int webSearchCalls
) {
    public record CitationDto(String title, String url) {
    }

    public record SourceDto(String title, String url) {
    }
}
