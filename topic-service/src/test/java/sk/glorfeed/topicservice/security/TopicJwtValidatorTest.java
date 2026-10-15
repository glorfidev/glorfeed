package sk.glorfeed.topicservice.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class TopicJwtValidatorTest {

    private static final String ISSUER = "https://auth.example.test/realms/glorfeed";
    private static final Instant NOW = Instant.now();
    private final OAuth2TokenValidator<Jwt> validator = TopicJwtValidator.forIssuerAndAudience(
        ISSUER, "glorfeed-topic-service");

    @Test
    void acceptsValidIssuerAudienceAndSubject() {
        assertThat(validator.validate(token(ISSUER, List.of("glorfeed-topic-service"), "user-123", NOW.minusSeconds(60), NOW.plusSeconds(60), null)).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(token("https://other.example.test/realms/glorfeed", List.of("glorfeed-topic-service"), "user-123", NOW.minusSeconds(60), NOW.plusSeconds(60), null)).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(token(ISSUER, List.of("account"), "user-123", NOW.minusSeconds(60), NOW.plusSeconds(60), null)).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        assertThat(validator.validate(token(ISSUER, List.of("glorfeed-topic-service"), "user-123", NOW.minusSeconds(120), NOW.minusSeconds(90), null)).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenNotYetValid() {
        assertThat(validator.validate(token(ISSUER, List.of("glorfeed-topic-service"), "user-123", NOW.plusSeconds(120), NOW.plusSeconds(300), NOW.plusSeconds(120))).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingOrBlankSubject() {
        assertThat(validator.validate(token(ISSUER, List.of("glorfeed-topic-service"), null, NOW.minusSeconds(60), NOW.plusSeconds(60), null)).hasErrors()).isTrue();
        assertThat(validator.validate(token(ISSUER, List.of("glorfeed-topic-service"), " ", NOW.minusSeconds(60), NOW.plusSeconds(60), null)).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingExpiration() {
        Jwt token = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .audience(List.of("glorfeed-topic-service"))
            .subject("user-123")
            .issuedAt(NOW.minusSeconds(60))
            .build();

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    private Jwt token(String issuer, List<String> audience, String subject, Instant issuedAt, Instant expiresAt, Instant notBefore) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt);
        if (subject != null) builder.subject(subject);
        if (notBefore != null) builder.notBefore(notBefore);
        return builder.build();
    }
}
