package sk.glorfeed.topicservice.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicJwtDecoderTest {

    private static final String ISSUER = "https://auth.example.test/realms/glorfeed";
    private static KeyPair signingKeyPair;
    private static KeyPair unrelatedKeyPair;
    private static JwtEncoder encoder;
    private static JwtDecoder decoder;

    @BeforeAll
    static void createKeysAndDecoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKeyPair = generator.generateKeyPair();
        unrelatedKeyPair = generator.generateKeyPair();

        RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) signingKeyPair.getPublic())
            .privateKey((RSAPrivateKey) signingKeyPair.getPrivate())
            .keyID("test-signing-key")
            .build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder
            .withPublicKey((RSAPublicKey) signingKeyPair.getPublic())
            .build();
        nimbusDecoder.setJwtValidator(TopicJwtValidator.forIssuerAndAudience(ISSUER, "glorfeed-topic-service"));
        decoder = nimbusDecoder;
    }

    @Test
    void acceptsValidSignedToken() {
        assertThat(decoder.decode(token(ISSUER, "user-123", List.of("glorfeed-topic-service"),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(120), null)).getSubject())
            .isEqualTo("user-123");
    }

    @Test
    void rejectsBadSignature() {
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new RSAKey.Builder((RSAPublicKey) unrelatedKeyPair.getPublic())
                .privateKey((RSAPrivateKey) unrelatedKeyPair.getPrivate())
                .keyID("test-signing-key")
                .build())));
        String token = encode(otherEncoder, ISSUER, "user-123", List.of("glorfeed-topic-service"),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(120), null);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceAndExpiredClaimsDuringDecode() {
        Instant now = Instant.now();
        assertRejected(token("https://other.example.test/realms/glorfeed", "user-123",
            List.of("glorfeed-topic-service"), now.minusSeconds(60), now.plusSeconds(120), null));
        assertRejected(token(ISSUER, "user-123", List.of("account"), now.minusSeconds(60), now.plusSeconds(120), null));
        assertRejected(token(ISSUER, "user-123", List.of("glorfeed-topic-service"),
            now.minusSeconds(120), now.minusSeconds(90), null));
        assertRejected(token(ISSUER, " ", List.of("glorfeed-topic-service"), now.minusSeconds(60), now.plusSeconds(120), null));
        assertRejected(token(ISSUER, "user-123", List.of("glorfeed-topic-service"),
            now.plusSeconds(120), now.plusSeconds(300), now.plusSeconds(120)));
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String token(String issuer, String subject, List<String> audience, Instant issuedAt,
                         Instant expiresAt, Instant notBefore) {
        return encode(encoder, issuer, subject, audience, issuedAt, expiresAt, notBefore);
    }

    private static String encode(JwtEncoder jwtEncoder, String issuer, String subject, List<String> audience,
                                 Instant issuedAt, Instant expiresAt, Instant notBefore) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .audience(audience)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt);
        if (notBefore != null) claims.notBefore(notBefore);
        return jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-signing-key").build(), claims.build()))
            .getTokenValue();
    }
}
