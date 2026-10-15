package sk.glorfeed.researchservice.research.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "research_run")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchRun {

    @Id
    private UUID id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "response_id", nullable = false, updatable = false, unique = true)
    private String responseId;

    @Column(nullable = false, updatable = false)
    private String model;

    @Column(name = "report_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
