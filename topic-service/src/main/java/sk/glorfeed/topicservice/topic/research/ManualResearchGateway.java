package sk.glorfeed.topicservice.topic.research;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import sk.glorfeed.topicservice.topic.api.dto.ResearchReportDto;
import sk.glorfeed.topicservice.topic.application.TopicService;

@Service
public class ManualResearchGateway {

    private static final Logger log = LoggerFactory.getLogger(ManualResearchGateway.class);

    private final TopicService topicService;
    private final InternalResearchClient researchClient;

    public ManualResearchGateway(TopicService topicService, InternalResearchClient researchClient) {
        this.topicService = topicService;
        this.researchClient = researchClient;
    }

    public ResearchReportDto run(UUID topicId, String ownerId) {
        log.info("Starting owner-checked manual research: topicId={}", topicId);
        topicService.getTopic(topicId, ownerId);
        return researchClient.run(topicId);
    }
}
