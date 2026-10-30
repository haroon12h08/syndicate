package com.syndicate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_PURPOSE = "purpose";
    public static final String PURPOSE_ACCESS = "ACCESS";
    public static final String PURPOSE_MFA = "MFA_CHALLENGE";
    public static final long MFA_CHALLENGE_EXPIRATION_MS = 300_000; // 5 minutes

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${syndicate.jwt.secret}") String secret,
                       @Value("${syndicate.jwt.expiration-ms:900000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String generateMfaChallengeToken(UUID userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + MFA_CHALLENGE_EXPIRATION_MS);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_PURPOSE, PURPOSE_MFA)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);
        String purpose = claims.get(CLAIM_PURPOSE, String.class);
        if (PURPOSE_MFA.equals(purpose)) {
            throw new JwtException("Challenge token cannot be used as access token");
        }
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractMfaUserId(String challengeToken) {
        Claims claims = parseClaims(challengeToken);
        String purpose = claims.get(CLAIM_PURPOSE, String.class);
        if (!PURPOSE_MFA.equals(purpose)) {
            throw new JwtException("Token is not an MFA challenge token");
        }
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
