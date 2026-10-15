package sk.glorfeed.researchservice.topic.services;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sk.glorfeed.researchservice.topic.domain.ResearchTopic;
import sk.glorfeed.researchservice.topic.repositories.ResearchTopicRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchTopicProjectionTest {

    @Mock
    private ResearchTopicRepository repository;

    @InjectMocks
    private ResearchTopicProjection projection;

    @Test
    void createsTopicAndTreatsRedeliveryAsIdempotent() {
        UUID topicId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(topicId)).thenReturn(false, true);

        projection.recordTopicCreated(eventId, topicId, "Climate", "Policy changes");
        projection.recordTopicCreated(eventId, topicId, "Climate", "Policy changes");

        org.mockito.ArgumentCaptor<ResearchTopic> saved = org.mockito.ArgumentCaptor.forClass(ResearchTopic.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(topicId);
        assertThat(saved.getValue().getSourceEventId()).isEqualTo(eventId);
        assertThat(saved.getValue().getName()).isEqualTo("Climate");
        verify(repository, org.mockito.Mockito.times(2)).existsById(topicId);
    }

    @Test
    void appliesUpdateAndDoesNotChangeTheInitialSourceEventId() {
        UUID topicId = UUID.randomUUID();
        UUID initialEventId = UUID.randomUUID();
        ResearchTopic topic = new ResearchTopic(topicId, "Climate", "Old description", initialEventId);
        when(repository.findById(topicId)).thenReturn(Optional.of(topic));

        projection.recordTopicUpdated(UUID.randomUUID(), topicId, "Climate policy", "Current description");

        assertThat(topic.getName()).isEqualTo("Climate policy");
        assertThat(topic.getDescription()).isEqualTo("Current description");
        assertThat(topic.getSourceEventId()).isEqualTo(initialEventId);
        verify(repository, never()).save(topic);
    }

    @Test
    void createsProjectionWhenUpdateArrivesBeforeCreate() {
        UUID topicId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(repository.findById(topicId)).thenReturn(Optional.empty());

        projection.recordTopicUpdated(eventId, topicId, "Climate", "Policy changes");

        org.mockito.ArgumentCaptor<ResearchTopic> saved = org.mockito.ArgumentCaptor.forClass(ResearchTopic.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(topicId);
        assertThat(saved.getValue().getName()).isEqualTo("Climate");
        assertThat(saved.getValue().getSourceEventId()).isEqualTo(eventId);
    }

    @Test
    void deletionIsIdempotent() {
        UUID topicId = UUID.randomUUID();
        when(repository.findById(topicId)).thenReturn(Optional.empty());

        projection.recordTopicDeleted(topicId);
        projection.recordTopicDeleted(topicId);

        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any(ResearchTopic.class));
    }

    @Test
    void rejectsInvalidBusinessDetailsInsteadOfTreatingThemAsTombstones() {
        assertThatThrownBy(() -> projection.recordTopicCreated(UUID.randomUUID(), UUID.randomUUID(), " ", "description"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(ResearchTopic.class));
    }
}
