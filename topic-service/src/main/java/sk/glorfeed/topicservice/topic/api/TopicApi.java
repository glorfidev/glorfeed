package sk.glorfeed.topicservice.topic.api;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;

@RequestMapping("/api/topics")
@Validated
@Tag(name = "Topics", description = "Create and manage monitored topics.")
public interface TopicApi {

    @Operation(
        operationId = "getTopics",
        summary = "List topics",
        description = "Returns all topics ordered from newest to oldest.",
        responses = @ApiResponse(responseCode = "200", description = "Topics retrieved")
    )
    @GetMapping
    List<TopicDto> getTopics(@AuthenticationPrincipal Jwt jwt);

    @Operation(
        operationId = "getTopic",
        summary = "Get a topic",
        description = "Returns one topic by its ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Topic retrieved"),
            @ApiResponse(responseCode = "404", description = "Topic not found", content = @Content)
        }
    )
    @GetMapping("/{topicId}")
    TopicDto getTopic(
        @PathVariable @Parameter(description = "ID of the topic to retrieve", required = true) UUID topicId,
        @AuthenticationPrincipal Jwt jwt
    );

    @Operation(
        operationId = "createTopic",
        summary = "Create a topic",
        description = "Creates a topic that can later be configured for research monitoring.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Topic created"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    TopicDto createTopic(@Valid @RequestBody CreateTopicDto createTopicDto, @AuthenticationPrincipal Jwt jwt);

    @Operation(
        operationId = "updateTopic",
        summary = "Update a topic",
        description = "Replaces a topic's name and description.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Topic updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Topic not found", content = @Content)
        }
    )
    @PutMapping("/{topicId}")
    TopicDto updateTopic(
        @PathVariable @Parameter(description = "ID of the topic to update", required = true) UUID topicId,
        @Valid @RequestBody UpdateTopicDto updateTopicDto,
        @AuthenticationPrincipal Jwt jwt
    );

    @Operation(
        operationId = "deleteTopic",
        summary = "Delete a topic",
        description = "Deletes a topic by its ID.",
        responses = {
            @ApiResponse(responseCode = "204", description = "Topic deleted"),
            @ApiResponse(responseCode = "404", description = "Topic not found", content = @Content)
        }
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{topicId}")
    void deleteTopic(
        @PathVariable @Parameter(description = "ID of the topic to delete", required = true) UUID topicId,
        @AuthenticationPrincipal Jwt jwt
    );
}
