package sk.glorfeed.researchservice.topic.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import sk.glorfeed.researchservice.topic.domain.ResearchTopic;

public interface ResearchTopicRepository extends JpaRepository<ResearchTopic, UUID> {
}
