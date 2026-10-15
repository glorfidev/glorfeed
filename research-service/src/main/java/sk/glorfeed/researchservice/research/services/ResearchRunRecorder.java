package sk.glorfeed.researchservice.research.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import sk.glorfeed.researchservice.outbox.domain.OutboxAggregateType;
import sk.glorfeed.researchservice.outbox.domain.OutboxEvent;
import sk.glorfeed.researchservice.outbox.domain.OutboxEventType;
import sk.glorfeed.researchservice.outbox.repositories.OutboxEventRepository;
import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.domain.ResearchRun;
import sk.glorfeed.researchservice.research.domain.ResearchSubject;
import sk.glorfeed.researchservice.research.repositories.ResearchRunRepository;

@Service
public class ResearchRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunRecorder.class);

    private final ResearchRunRepository repository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public ResearchRunRecorder(ResearchRunRepository repository, OutboxEventRepository outboxEventRepository,
                               JsonMapper jsonMapper) {
        this.repository = repository;
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public UUID record(ResearchSubject subject, ResearchReport report) {
        log.info("Recording completed research: topicId={}, responseId={}", subject.topicId(), report.responseId());
        if (!subject.topicId().equals(report.topicId())) {
            throw new IllegalArgumentException("Research report topic does not match its subject");
        }

        UUID runId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        repository.save(new ResearchRun(
                runId,
                report.topicId(),
                report.responseId(),
                report.model(),
                jsonMapper.writeValueAsString(report),
                createdAt
        ));

        Map<String, Object> reportPayload = Map.of(
                "topicId", report.topicId().toString(),
                "responseId", report.responseId(),
                "model", report.model(),
                "text", report.text(),
                "citations", report.citations().stream()
                        .map(citation -> Map.of("title", citation.title(), "url", citation.url())).toList(),
                "sources", report.sources().stream()
                        .map(source -> Map.of("title", source.title(), "url", source.url())).toList(),
                "inputTokens", report.inputTokens(),
                "outputTokens", report.outputTokens(),
                "webSearchCalls", report.webSearchCalls()
        );
        Map<String, Object> eventPayload = Map.of(
                "runId", runId.toString(),
                "topicId", subject.topicId().toString(),
                "name", subject.name(),
                "description", subject.description(),
                "report", reportPayload
        );
        outboxEventRepository.save(new OutboxEvent(
                UUID.randomUUID(),
                subject.topicId().toString(),
                OutboxAggregateType.RESEARCH.value(),
                OutboxEventType.RESEARCH_COMPLETED.value(),
                eventPayload,
                createdAt
        ));
        return runId;
    }
}
