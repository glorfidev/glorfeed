package sk.glorfeed.researchservice.research.domain;

import java.util.UUID;

public record ResearchSubject(UUID topicId, String name, String description) {
}
