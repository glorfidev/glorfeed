package sk.glorfeed.researchservice.research.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import sk.glorfeed.researchservice.research.domain.ResearchRun;

public interface ResearchRunRepository extends JpaRepository<ResearchRun, UUID> {
}
