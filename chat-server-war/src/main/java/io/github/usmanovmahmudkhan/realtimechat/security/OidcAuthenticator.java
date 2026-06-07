package io.github.usmanovmahmudkhan.realtimechat.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.port.IdentityRepository;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolException;

import java.net.URI;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public final class OidcAuthenticator {
    private final String audience;
    private final IdentityRepository identities;
    private final Map<String, DefaultJWTProcessor<SecurityContext>> processors;

    public OidcAuthenticator(Set<URI> issuers, String audience, IdentityRepository identities) {
        this.audience = audience;
        this.identities = identities;
        Map<String, DefaultJWTProcessor<SecurityContext>> configured = new HashMap<>();
        try {
            for (URI issuer : issuers) {
                String metadataUrl = issuer.toString().replaceAll("/$", "") + "/.well-known/openid-configuration";
                var metadata = new ObjectMapper().readTree(
                        new DefaultResourceRetriever(2_000, 2_000).retrieveResource(URI.create(metadataUrl).toURL())
                                .getContent());
                String jwksUri = metadata.required("jwks_uri").asText();
                JWKSource<SecurityContext> source = JWKSourceBuilder
                        .create(URI.create(jwksUri).toURL()).retrying(true).build();
                DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
                processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
                JWTClaimsSet exact = new JWTClaimsSet.Builder().issuer(issuer.toString()).build();
                processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(audience, exact,
                        Set.of("sub", "iss", "aud", "exp", "iat")));
                configured.put(issuer.toString(), processor);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize OIDC verifier", exception);
        }
        this.processors = Map.copyOf(configured);
    }

    public AuthenticatedUser authenticate(String authorizationHeader, UUID tenantId) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ProtocolException(ErrorCode.UNAUTHENTICATED, "Bearer token required");
        }
        try {
            String token = authorizationHeader.substring(7);
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            DefaultJWTProcessor<SecurityContext> processor = processors.get(issuer);
            if (processor == null) {
                throw new ProtocolException(ErrorCode.UNAUTHENTICATED, "Token issuer is not approved");
            }
            JWTClaimsSet claims = processor.process(token, null);
            return identities.findActiveIdentity(issuer, claims.getSubject(), tenantId)
                    .orElseThrow(() -> new ProtocolException(ErrorCode.UNAUTHENTICATED, "Identity is not active"));
        } catch (ProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProtocolException(ErrorCode.UNAUTHENTICATED, "Invalid access token", exception);
        }
    }
}
