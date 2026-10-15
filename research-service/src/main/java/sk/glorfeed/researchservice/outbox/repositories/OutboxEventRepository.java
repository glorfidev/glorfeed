package sk.glorfeed.researchservice.outbox.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import sk.glorfeed.researchservice.outbox.domain.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
