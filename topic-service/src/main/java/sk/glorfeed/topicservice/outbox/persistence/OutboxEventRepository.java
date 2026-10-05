package sk.glorfeed.topicservice.outbox.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import sk.glorfeed.topicservice.outbox.domain.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
