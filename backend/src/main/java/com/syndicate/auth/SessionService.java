package com.syndicate.auth;

import com.syndicate.common.ChecksumUtil;
import com.syndicate.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keeps people signed in without leaving a long-lived key in the browser.
 *
 * <p>The browser holds only a cookie it cannot read, scoped to the sign-in routes; the short
 * access token lives in the page's memory and dies with the tab. Each refresh rotates the cookie,
 * so a copied cookie stops working the moment either copy is used again - and that reuse revokes
 * the whole chain rather than quietly letting both continue.
 */
@Service
public class SessionService {

    public static final String COOKIE = "syndicate_session";

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRevoker sessionRevoker;
    private final Duration lifetime;
    private final boolean secureCookie;

    public SessionService(RefreshTokenRepository refreshTokenRepository, SessionRevoker sessionRevoker,
                          @Value("${syndicate.session.refresh-days:14}") int refreshDays,
                          @Value("${syndicate.session.secure-cookie:true}") boolean secureCookie) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRevoker = sessionRevoker;
        this.lifetime = Duration.ofDays(refreshDays);
        this.secureCookie = secureCookie;
    }

    @Transactional
    public void startSession(User user, HttpServletRequest request, HttpServletResponse response) {
        issue(user, UUID.randomUUID(), request, response);
    }

    /**
     * Exchanges the cookie for a new one and tells the caller who it belongs to.
     *
     * @throws BadCredentialsException when there is nothing valid to exchange
     */
    @Transactional
    public User refreshSession(HttpServletRequest request, HttpServletResponse response) {
        String presented = readCookie(request);
        if (presented == null) {
            throw new BadCredentialsException("No session");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(presented))
                .orElseThrow(() -> new BadCredentialsException("No session"));

        if (!token.isUsable()) {
            // a token that has already been exchanged is either a replay or a stolen copy
            if (token.getUsedAt() != null) {
                log.warn("Refresh token reuse detected for user {}; ending every session in that chain",
                        token.getUser().getId());
                sessionRevoker.revokeFamily(token.getFamilyId(), "Reuse of an already-exchanged session token");
            }
            clearCookie(response);
            throw new BadCredentialsException("Session expired");
        }

        token.markUsed();
        issue(token.getUser(), token.getFamilyId(), request, response);
        return token.getUser();
    }

    @Transactional
    public void endSession(HttpServletRequest request, HttpServletResponse response) {
        String presented = readCookie(request);
        if (presented != null) {
            refreshTokenRepository.findByTokenHash(hash(presented))
                    .ifPresent(token -> refreshTokenRepository.findByFamilyId(token.getFamilyId())
                            .forEach(t -> t.revoke("Signed out")));
        }
        clearCookie(response);
    }

    private void issue(User user, UUID familyId, HttpServletRequest request, HttpServletResponse response) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        refreshTokenRepository.save(new RefreshToken(user, hash(value), familyId,
                Instant.now().plus(lifetime), userAgent(request)));
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(lifetime)
                .build()
                .toString());
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, "")
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/auth").maxAge(0)
                .build().toString());
    }

    private static String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String userAgent(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent == null ? null : agent.substring(0, Math.min(agent.length(), 255));
    }

    private static String hash(String value) {
        return ChecksumUtil.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }
}
