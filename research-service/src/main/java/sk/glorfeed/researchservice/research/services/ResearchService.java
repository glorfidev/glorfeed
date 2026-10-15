package sk.glorfeed.researchservice.research.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.domain.ResearchSubject;
import sk.glorfeed.researchservice.research.providers.OpenAiResearchClient;
import sk.glorfeed.researchservice.topic.domain.ResearchTopic;
import sk.glorfeed.researchservice.topic.repositories.ResearchTopicRepository;

@Service
public class ResearchService {

    private static final Logger log = LoggerFactory.getLogger(ResearchService.class);

    private final ResearchTopicRepository topicRepository;
    private final OpenAiResearchClient researchClient;
    private final ResearchRunRecorder runRecorder;

    public ResearchService(ResearchTopicRepository topicRepository, OpenAiResearchClient researchClient,
                           ResearchRunRecorder runRecorder) {
        this.topicRepository = topicRepository;
        this.researchClient = researchClient;
        this.runRecorder = runRecorder;
    }

    public ResearchReport researchTopic(UUID topicId) {
        log.info("Starting manual research for topicId={}", topicId);
        ResearchTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research topic not found"));
        ResearchSubject subject = new ResearchSubject(topic.getId(), topic.getName(), topic.getDescription());
        ResearchReport report = researchClient.research(subject);
        UUID runId = runRecorder.record(subject, report);
        log.info("Completed manual research for topicId={}, runId={}, responseId={}, webSearchCalls={}",
                topicId, runId, report.responseId(), report.webSearchCalls());
        return report;
    }
}
