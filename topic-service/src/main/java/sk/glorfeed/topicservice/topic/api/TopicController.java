package sk.glorfeed.topicservice.topic.api;

import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;
import sk.glorfeed.topicservice.topic.application.TopicService;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TopicController implements TopicApi {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @Override
    public List<TopicDto> getTopics() {
        return topicService.getTopics();
    }

    @Override
    public TopicDto getTopic(UUID topicId) {
        return topicService.getTopic(topicId);
    }

    @Override
    public TopicDto createTopic(CreateTopicDto createTopicDto) {
        return topicService.createTopic(createTopicDto);
    }

    @Override
    public TopicDto updateTopic(UUID topicId, UpdateTopicDto updateTopicDto) {
        return topicService.updateTopic(topicId, updateTopicDto);
    }

    @Override
    public void deleteTopic(UUID topicId) {
        topicService.deleteTopic(topicId);
    }
}
