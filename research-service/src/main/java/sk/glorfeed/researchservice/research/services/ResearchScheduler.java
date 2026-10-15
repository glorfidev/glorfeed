package sk.glorfeed.researchservice.research.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sk.glorfeed.researchservice.research.domain.ResearchSubject;
import sk.glorfeed.researchservice.topic.domain.ResearchTopic;
import sk.glorfeed.researchservice.topic.repositories.ResearchTopicRepository;

@Service
public class ResearchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResearchScheduler.class);

    private final ResearchTopicRepository topicRepository;

    public ResearchScheduler(ResearchTopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Scheduled(
            fixedDelayString = "${research.schedule.interval}",
            initialDelayString = "${research.schedule.initial-delay}"
    )
    @Transactional(readOnly = true)
    public void scanTopics() {
        log.info("Starting scheduled research topic scan");

        List<ResearchTopic> topics = topicRepository.findAll();
        log.info("Found {} topics to investigate", topics.size());

        for (ResearchTopic topic : topics) {
            ResearchSubject subject = new ResearchSubject(topic.getId(), topic.getName(), topic.getDescription());
            log.info("Research candidate: topicId={}, name={}", subject.topicId(), subject.name());
        }
    }
}
