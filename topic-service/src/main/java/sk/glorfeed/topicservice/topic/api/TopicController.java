package sk.glorfeed.topicservice.topic.api;

import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;
import sk.glorfeed.topicservice.topic.application.TopicService;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
public class TopicController implements TopicApi {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @Override
    public List<TopicDto> getTopics(@AuthenticationPrincipal Jwt jwt) {
        return topicService.getTopics(jwt.getSubject());
    }

    @Override
    public TopicDto getTopic(UUID topicId, @AuthenticationPrincipal Jwt jwt) {
        return topicService.getTopic(topicId, jwt.getSubject());
    }

    @Override
    public TopicDto createTopic(CreateTopicDto createTopicDto, @AuthenticationPrincipal Jwt jwt) {
        return topicService.createTopic(createTopicDto, jwt.getSubject());
    }

    @Override
    public TopicDto updateTopic(UUID topicId, UpdateTopicDto updateTopicDto, @AuthenticationPrincipal Jwt jwt) {
        return topicService.updateTopic(topicId, updateTopicDto, jwt.getSubject());
    }

    @Override
    public void deleteTopic(UUID topicId, @AuthenticationPrincipal Jwt jwt) {
        topicService.deleteTopic(topicId, jwt.getSubject());
    }
}
