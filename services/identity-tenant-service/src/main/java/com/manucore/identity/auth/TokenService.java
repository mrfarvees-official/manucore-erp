package com.manucore.identity.auth;

import com.manucore.identity.domain.UserAccount;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final RSAKey rsaKey;
    private final String issuer;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder,
                        RSAKey rsaKey,
                        @Value("${manucore.identity.issuer:manucore-identity}") String issuer,
                        @Value("${manucore.identity.token-ttl:PA1H}") Duration ttl) {
        this.encoder = encoder;
        this.rsaKey = rsaKey;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public LoginResponse issue(UserAccount user) {
        Instant now = Instant.now();
        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("tenant_id", user.getTenantId())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKey.getKeyID())
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", ttl.toSeconds());
    }
}
