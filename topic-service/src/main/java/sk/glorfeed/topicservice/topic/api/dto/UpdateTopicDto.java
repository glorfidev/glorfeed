package sk.glorfeed.topicservice.topic.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTopicDto(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 10_000) String description
) {
}
