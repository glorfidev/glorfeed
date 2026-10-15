package sk.glorfeed.topicservice.topic.research;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sk.glorfeed.topicservice.topic.api.dto.ResearchReportDto;
import sk.glorfeed.topicservice.topic.application.TopicNotFoundException;
import sk.glorfeed.topicservice.topic.application.TopicService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualResearchGatewayTest {

    private static final String OWNER = "verified-subject";

    @Mock
    private TopicService topicService;

    @Mock
    private InternalResearchClient researchClient;

    @InjectMocks
    private ManualResearchGateway gateway;

    @Test
    void checksVerifiedOwnerBeforeForwardingResearch() {
        UUID topicId = UUID.randomUUID();
        ResearchReportDto report = new ResearchReportDto(
                topicId, "response-1", "gpt-6-luna", "Report", List.of(), List.of(), 1, 2, 1);
        when(researchClient.run(topicId)).thenReturn(report);

        assertThat(gateway.run(topicId, OWNER)).isSameAs(report);

        var order = inOrder(topicService, researchClient);
        order.verify(topicService).getTopic(topicId, OWNER);
        order.verify(researchClient).run(topicId);
    }

    @Test
    void doesNotForwardResearchForTopicOutsideVerifiedOwnerScope() {
        UUID topicId = UUID.randomUUID();
        when(topicService.getTopic(topicId, OWNER)).thenThrow(new TopicNotFoundException(topicId));

        assertThatThrownBy(() -> gateway.run(topicId, OWNER))
                .isInstanceOf(TopicNotFoundException.class);

        verify(researchClient, never()).run(topicId);
    }
}
