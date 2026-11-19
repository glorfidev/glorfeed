package sk.glorfeed.researchservice.research.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.services.ResearchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InternalResearchControllerTest {

    @Test
    void requiresEnabledGatewayAndMatchingInternalToken() {
        ResearchService service = mock(ResearchService.class);
        UUID topicId = UUID.randomUUID();
        ResearchReport report = new ResearchReport(topicId, "response", "model", "text",
                List.of(), List.of(), 1, 2, 1);
        when(service.researchTopic(topicId)).thenReturn(report);

        InternalResearchController enabled = new InternalResearchController(service, "internal-secret", true);
        assertThat(enabled.run(topicId, "internal-secret")).isSameAs(report);

        assertThatThrownBy(() -> enabled.run(topicId, "wrong-secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> enabled.run(topicId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        InternalResearchController disabled = new InternalResearchController(service, "", false);
        assertThatThrownBy(() -> disabled.run(topicId, "internal-secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        InternalResearchController missingToken = new InternalResearchController(service, "  ", true);
        assertThatThrownBy(() -> missingToken.run(topicId, "internal-secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(service).researchTopic(topicId);
        verifyNoMoreInteractions(service);
    }
}
