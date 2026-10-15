package sk.glorfeed.topicservice.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class TopicJwtValidator {

    private TopicJwtValidator() {
    }

    public static OAuth2TokenValidator<Jwt> forIssuerAndAudience(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            boolean validAudience = jwt.getAudience() != null && jwt.getAudience().contains(audience);
            boolean validSubject = jwt.getSubject() != null && !jwt.getSubject().isBlank();
            boolean hasExpiration = jwt.getExpiresAt() != null;
            return validAudience && validSubject && hasExpiration
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Token must contain an expiration, the configured audience, and a nonblank subject", null));
        };
        return jwt -> {
            OAuth2TokenValidatorResult standardResult = standard.validate(jwt);
            return standardResult.hasErrors() ? standardResult : requiredClaims.validate(jwt);
        };
    }
}
