package sk.glorfeed.researchservice.research.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import sk.glorfeed.researchservice.research.services.ResearchService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchControllerTest {

    @Test
    void allowsSameOriginLoopbackAndRejectsCrossOriginOrUntrustedRequests() {
        ResearchService service = mock(ResearchService.class);
        ResearchController controller = new ResearchController(service);
        UUID topicId = UUID.randomUUID();

        for (String address : new String[] {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1"}) {
            HttpServletRequest request = request(address, "http://localhost:8081");
            controller.run(topicId, request);
        }
        controller.run(topicId, request("127.0.0.1", null));
        controller.run(topicId, request("127.0.0.1", "http://127.0.0.1", "127.0.0.1", "http", 80));
        controller.run(topicId, request("::1", "http://[::1]", "::1", "http", 80));

        for (String address : new String[] {"192.0.2.5", "localhost", "", null}) {
            HttpServletRequest request = request(address, "http://localhost:8081");
            when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
            assertThatThrownBy(() -> controller.run(topicId, request))
                    .hasMessageContaining("localhost");
        }

        for (String origin : new String[] {"https://attacker.example", "null", "", "not an origin",
                "http://attacker.example:8081", "http://localhost:8082"}) {
            HttpServletRequest request = request("127.0.0.1", origin);
            assertThatThrownBy(() -> controller.run(topicId, request))
                    .hasMessageContaining("localhost");
        }
        HttpServletRequest attackerHost = request("127.0.0.1", "http://attacker.example:8081",
                "attacker.example", "http", 8081);
        assertThatThrownBy(() -> controller.run(topicId, attackerHost)).hasMessageContaining("localhost");

        verify(service, org.mockito.Mockito.times(6)).researchTopic(topicId);
    }

    private HttpServletRequest request(String remoteAddress, String origin) {
        return request(remoteAddress, origin, "localhost", "http", 8081);
    }

    private HttpServletRequest request(String remoteAddress, String origin, String serverName,
                                       String scheme, int serverPort) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("Origin")).thenReturn(origin);
        when(request.getScheme()).thenReturn(scheme);
        when(request.getServerName()).thenReturn(serverName);
        when(request.getServerPort()).thenReturn(serverPort);
        return request;
    }
}
