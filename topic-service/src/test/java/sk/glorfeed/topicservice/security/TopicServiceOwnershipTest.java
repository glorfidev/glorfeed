package sk.glorfeed.topicservice.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sk.glorfeed.topicservice.outbox.persistence.OutboxEventRepository;
import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.application.TopicNotFoundException;
import sk.glorfeed.topicservice.topic.application.TopicService;
import sk.glorfeed.topicservice.topic.domain.Topic;
import sk.glorfeed.topicservice.topic.persistence.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceOwnershipTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private TopicService topicService;

    @Test
    void listQueriesOnlyTheVerifiedOwner() {
        when(topicRepository.findAllByOwnerIdOrderByCreatedAtDesc("subject-a")).thenReturn(List.of());

        topicService.getTopics("subject-a");

        verify(topicRepository).findAllByOwnerIdOrderByCreatedAtDesc("subject-a");
    }

    @Test
    void createAssignsTheVerifiedOwnerAndNeverAcceptsAnOwnerFromTheInputDto() {
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> {
            Topic topic = invocation.getArgument(0);
            topic.setId(UUID.randomUUID());
            return topic;
        });

        topicService.createTopic(new CreateTopicDto("Topic", "Description"), "subject-a");

        org.mockito.ArgumentCaptor<Topic> saved = org.mockito.ArgumentCaptor.forClass(Topic.class);
        verify(topicRepository).save(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo("subject-a");
        verify(outboxEventRepository).save(any());
    }

    @Test
    void foreignAndLegacyOwnerlessTopicsAreNotFound() {
        UUID id = UUID.randomUUID();
        when(topicRepository.findByIdAndOwnerId(id, "subject-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.getTopic(id, "subject-a"))
            .isInstanceOf(TopicNotFoundException.class);
        verify(topicRepository).findByIdAndOwnerId(id, "subject-a");
    }
}
