package sk.glorfeed.topicservice.topic.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(UUID topicId) {
        super("Topic with id '%s' was not found.".formatted(topicId));
    }
}
