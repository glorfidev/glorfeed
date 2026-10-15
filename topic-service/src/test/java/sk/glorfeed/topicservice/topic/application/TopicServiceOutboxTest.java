package sk.glorfeed.topicservice.topic.application;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sk.glorfeed.topicservice.outbox.domain.OutboxEvent;
import sk.glorfeed.topicservice.outbox.domain.OutboxEventType;
import sk.glorfeed.topicservice.outbox.persistence.OutboxEventRepository;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;
import sk.glorfeed.topicservice.topic.domain.Topic;
import sk.glorfeed.topicservice.topic.persistence.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceOutboxTest {

    private static final String OWNER = "verified-subject";

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private TopicService topicService;

    @Test
    void updateEmitsOwnerScopedUpdatedEventInTheSameServiceOperation() {
        UUID topicId = UUID.randomUUID();
        Topic topic = Topic.builder().id(topicId).name("Old name").description("Old description").ownerId(OWNER).build();
        when(topicRepository.findByIdAndOwnerId(topicId, OWNER)).thenReturn(Optional.of(topic));

        topicService.updateTopic(topicId, new UpdateTopicDto("New name", "New description"), OWNER);

        verify(topicRepository).flush();
        OutboxEvent event = capturedEvent();
        assertThat(event.getAggregateId()).isEqualTo(topicId.toString());
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.TOPIC_UPDATED.value());
        assertThat(event.getPayload()).containsEntry("topicId", topicId.toString())
            .containsEntry("name", "New name").containsEntry("description", "New description");
    }

    @Test
    void deleteEmitsTombstoneBusinessEventOnlyAfterOwnerScopedLookup() {
        UUID topicId = UUID.randomUUID();
        Topic topic = Topic.builder().id(topicId).name("Name").description("Description").ownerId(OWNER).build();
        when(topicRepository.findByIdAndOwnerId(topicId, OWNER)).thenReturn(Optional.of(topic));

        topicService.deleteTopic(topicId, OWNER);

        verify(topicRepository).delete(topic);
        verify(topicRepository).flush();
        OutboxEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.TOPIC_DELETED.value());
        assertThat(event.getPayload()).containsEntry("topicId", topicId.toString());
    }

    @Test
    void foreignTopicMutationEmitsNoEvent() {
        UUID topicId = UUID.randomUUID();
        when(topicRepository.findByIdAndOwnerId(topicId, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.deleteTopic(topicId, OWNER)).isInstanceOf(TopicNotFoundException.class);

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(topicRepository, never()).delete(any(Topic.class));
    }

    private OutboxEvent capturedEvent() {
        org.mockito.ArgumentCaptor<OutboxEvent> event = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(event.capture());
        return event.getValue();
    }
}
