package sk.glorfeed.topicservice.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import sk.glorfeed.topicservice.topic.api.TopicController;
import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.application.TopicService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TopicController.class)
@Import(ResourceServerSecurityConfiguration.class)
class TopicAuthorizationTest {

    private static final String OWNER = "keycloak-subject-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousTopicRequestsReceiveUnauthorized() throws Exception {
        mockMvc.perform(get("/api/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    void decoderRejectionsReceiveUnauthorized() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid signature"));

        mockMvc.perform(get("/api/topics").header("Authorization", "Bearer forged-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void everyTopicOperationUsesAuthenticatedSubjectAsOwnerScope() throws Exception {
        UUID id = UUID.randomUUID();
        TopicDto topic = new TopicDto(id, "Climate", "Climate research", Instant.now());
        when(topicService.getTopics(OWNER)).thenReturn(List.of(topic));
        when(topicService.getTopic(id, OWNER)).thenReturn(topic);
        when(topicService.createTopic(any(CreateTopicDto.class), eq(OWNER))).thenReturn(topic);
        when(topicService.updateTopic(eq(id), any(), eq(OWNER))).thenReturn(topic);

        mockMvc.perform(get("/api/topics").with(jwt().jwt(jwt -> jwt.subject(OWNER))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/topics/{id}", id).with(jwt().jwt(jwt -> jwt.subject(OWNER))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/topics").with(jwt().jwt(jwt -> jwt.subject(OWNER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Climate\",\"description\":\"Climate research\",\"ownerId\":\"forged-owner\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/topics/{id}", id).with(jwt().jwt(jwt -> jwt.subject(OWNER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Climate\",\"description\":\"Updated research\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/topics/{id}", id).with(jwt().jwt(jwt -> jwt.subject(OWNER))))
            .andExpect(status().isNoContent());

        verify(topicService).getTopics(OWNER);
        verify(topicService).getTopic(id, OWNER);
        verify(topicService).createTopic(new CreateTopicDto("Climate", "Climate research"), OWNER);
        verify(topicService).updateTopic(eq(id), any(), eq(OWNER));
        verify(topicService).deleteTopic(id, OWNER);
    }
}
