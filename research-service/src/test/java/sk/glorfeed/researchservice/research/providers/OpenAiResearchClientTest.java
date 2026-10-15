package sk.glorfeed.researchservice.research.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionWebSearch;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseOutputText.Annotation.UrlCitation;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.models.responses.WebSearchTool;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import sk.glorfeed.researchservice.research.domain.ResearchSubject;

class OpenAiResearchClientTest {

    private static final String MODEL = "gpt-6-luna";
    private static final UUID TOPIC_ID = UUID.fromString("b6bc9f78-9a18-4ab8-9d0f-e063768b64d5");

    private OpenAIClient openAIClient;
    private ResponseService responseService;
    private OpenAiResearchClient researchClient;

    @BeforeEach
    void setUp() {
        openAIClient = mock(OpenAIClient.class);
        responseService = mock(ResponseService.class);
        when(openAIClient.responses()).thenReturn(responseService);
        researchClient = new OpenAiResearchClient(openAIClient, MODEL);
    }

    @Test
    void sendsBoundedStatelessLowReasoningWebSearchRequestAndMapsCitationsAndUsage() {
        var one = "https://example.test/one";
        var two = "https://example.test/two";
        var response = successfulResponse(List.of(
                searchItem(one, one, ""),
                messageItem("A recent development. [Source](https://example.test/one) Also see https://example.test/two.",
                        citation("First source", one), citation("", two))), 111);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(response);

        var report = researchClient.research(new ResearchSubject(TOPIC_ID, "Climate policy", "Track new rules."));

        assertThat(report.topicId()).isEqualTo(TOPIC_ID);
        assertThat(report.responseId()).isEqualTo("resp-test");
        assertThat(report.model()).isEqualTo(MODEL);
        assertThat(report.text()).contains("A recent development.");
        assertThat(report.citations()).containsExactly(
                new sk.glorfeed.researchservice.research.domain.ResearchCitation("First source", one),
                new sk.glorfeed.researchservice.research.domain.ResearchCitation(two, two));
        assertThat(report.sources()).containsExactly(
                new sk.glorfeed.researchservice.research.domain.ResearchSource("First source", one),
                new sk.glorfeed.researchservice.research.domain.ResearchSource(two, two));
        assertThat(report.inputTokens()).isEqualTo(111);
        assertThat(report.outputTokens()).isEqualTo(37);
        assertThat(report.webSearchCalls()).isEqualTo(1);

        var params = org.mockito.ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(responseService).create(params.capture());
        var request = params.getValue();
        assertThat(request.model().orElseThrow().string()).contains(MODEL);
        assertThat(request.input().orElseThrow().asText()).isEqualTo("Topic: Climate policy\nDescription: Track new rules.");
        assertThat(request.maxToolCalls()).contains(2L);
        assertThat(request.maxOutputTokens()).contains(2500L);
        assertThat(request.store()).contains(false);
        assertThat(request.reasoning().orElseThrow().effort()).contains(com.openai.models.ReasoningEffort.LOW);
        assertThat(request.toolChoice().orElseThrow().options()).contains(ToolChoiceOptions.REQUIRED);
        assertThat(request.include().orElseThrow()).contains(ResponseIncludable.of("web_search_call.action.sources"));
        assertThat(request.tools().orElseThrow()).singleElement().satisfies(tool -> {
            var webSearch = tool.webSearch().orElseThrow();
            assertThat(webSearch.type()).isEqualTo(WebSearchTool.Type.WEB_SEARCH);
            assertThat(webSearch.searchContextSize()).contains(WebSearchTool.SearchContextSize.LOW);
        });
    }

    @Test
    void rejectsMissingCredentialsWithoutCallingOpenAi() {
        var withoutKey = new OpenAiResearchClient("", MODEL);

        assertThatThrownBy(() -> withoutKey.research(subject()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
        org.mockito.Mockito.verifyNoInteractions(openAIClient);
    }

    @Test
    void mapsSdkProviderFailuresToBadGateway() {
        when(responseService.create(any(ResponseCreateParams.class))).thenThrow(new OpenAIException("provider failure"));

        assertBadGateway(() -> researchClient.research(subject()));
    }

    @Test
    void rejectsIncompleteResponsesAndResponsesWithoutSearchOrText() {
        var incomplete = mock(Response.class);
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(incomplete);
        assertBadGateway(() -> researchClient.research(subject()));

        var withoutSearch = successfulResponse(List.of(messageItem("Report without web search.")), 0);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(withoutSearch);
        assertBadGateway(() -> researchClient.research(subject()));

        var withoutText = successfulResponse(
                List.of(searchItem("https://example.test/source"), messageItem("  \n")), 0);
        when(responseService.create(any(ResponseCreateParams.class))).thenReturn(withoutText);
        assertBadGateway(() -> researchClient.research(subject()));
    }

    private static void assertBadGateway(org.assertj.core.api.ThrowableAssert.ThrowingCallable request) {
        assertThatThrownBy(request).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(502));
    }

    private static ResearchSubject subject() {
        return new ResearchSubject(TOPIC_ID, "Climate policy", "Track new rules.");
    }

    private static Response successfulResponse(List<ResponseOutputItem> output, int inputTokens) {
        var response = mock(Response.class);
        var usage = mock(ResponseUsage.class);
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        when(response.output()).thenReturn(output);
        when(response.id()).thenReturn("resp-test");
        when(response.model()).thenReturn(ResponsesModel.ofString(MODEL));
        when(response.usage()).thenReturn(Optional.of(usage));
        when(usage.inputTokens()).thenReturn((long) inputTokens);
        when(usage.outputTokens()).thenReturn(37L);
        return response;
    }

    private static ResponseOutputItem searchItem(String... urls) {
        var sources = java.util.Arrays.stream(urls).map(url -> {
            var source = mock(ResponseFunctionWebSearch.Action.Search.Source.class);
            when(source.url()).thenReturn(url);
            return source;
        }).toList();
        var search = mock(ResponseFunctionWebSearch.Action.Search.class);
        when(search.sources()).thenReturn(Optional.of(sources));
        var action = mock(ResponseFunctionWebSearch.Action.class);
        when(action.search()).thenReturn(Optional.of(search));
        var call = mock(ResponseFunctionWebSearch.class);
        when(call.action()).thenReturn(action);
        var item = mock(ResponseOutputItem.class);
        when(item.webSearchCall()).thenReturn(Optional.of(call));
        when(item.message()).thenReturn(Optional.empty());
        return item;
    }

    private static ResponseOutputItem messageItem(String text, UrlCitation... citations) {
        var annotations = java.util.Arrays.stream(citations).map(citation -> {
            var annotation = mock(ResponseOutputText.Annotation.class);
            when(annotation.urlCitation()).thenReturn(Optional.of(citation));
            return annotation;
        }).toList();
        var outputText = mock(ResponseOutputText.class);
        when(outputText.text()).thenReturn(text);
        when(outputText.annotations()).thenReturn(annotations);
        var content = mock(ResponseOutputMessage.Content.class);
        when(content.outputText()).thenReturn(Optional.of(outputText));
        var message = mock(ResponseOutputMessage.class);
        when(message.content()).thenReturn(List.of(content));
        var item = mock(ResponseOutputItem.class);
        when(item.message()).thenReturn(Optional.of(message));
        when(item.webSearchCall()).thenReturn(Optional.empty());
        return item;
    }

    private static UrlCitation citation(String title, String url) {
        var citation = mock(UrlCitation.class);
        when(citation.title()).thenReturn(title);
        when(citation.url()).thenReturn(url);
        return citation;
    }
}
