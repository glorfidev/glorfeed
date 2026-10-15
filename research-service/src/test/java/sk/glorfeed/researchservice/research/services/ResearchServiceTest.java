package sk.glorfeed.researchservice.research.services;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.providers.OpenAiResearchClient;
import sk.glorfeed.researchservice.topic.domain.ResearchTopic;
import sk.glorfeed.researchservice.topic.repositories.ResearchTopicRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchServiceTest {

    @Mock
    private ResearchTopicRepository topicRepository;

    @Mock
    private OpenAiResearchClient researchClient;

    @Mock
    private ResearchRunRecorder runRecorder;

    @InjectMocks
    private ResearchService researchService;

    @Test
    void rejectsUnknownTopicBeforeCallingProvider() {
        UUID topicId = UUID.randomUUID();
        when(topicRepository.findById(topicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> researchService.researchTopic(topicId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(researchClient, never()).research(org.mockito.ArgumentMatchers.any());
        verify(runRecorder, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providerFailureDoesNotPersistAResearchRun() {
        UUID topicId = UUID.randomUUID();
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(
                new ResearchTopic(topicId, "Topic", "Description", UUID.randomUUID())));
        when(researchClient.research(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> researchService.researchTopic(topicId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        verify(runRecorder, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
