package com.syndicate.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards the endpoints anyone can reach without an account. Sign-in and sign-up are the doors to
 * confidential deal documents, so a single source cannot sit there guessing.
 *
 * <p>Counts are kept in this process. One instance is the normal deployment; with several, each
 * applies the limit to the traffic it sees, which still bounds an attacker by the same order.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Integer> limits;
    private final int windowSeconds;

    public RateLimitFilter(@Value("${syndicate.rate-limit.window-seconds:300}") int windowSeconds,
                           @Value("${syndicate.rate-limit.sign-in:10}") int signInLimit,
                           @Value("${syndicate.rate-limit.sign-up:5}") int signUpLimit) {
        this.windowSeconds = windowSeconds;
        this.limits = Map.of("/api/auth/login", signInLimit, "/api/auth/register", signUpLimit);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Integer limit = limits.get(request.getRequestURI());
        if (limit == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRequestURI() + "|" + clientAddress(request);
        if (exceeded(key, limit)) {
            log.warn("Rate limit reached for {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", "RATE_LIMITED",
                    "Too many attempts. Wait a few minutes and try again.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceeded(String key, int limit) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        Deque<Instant> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return true;
            }
            window.addLast(Instant.now());
            return false;
        }
    }

    /** Honours a reverse proxy's forwarded address, which is how this runs behind TLS. */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Keeps the map from growing without bound on a long-running instance. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    void evictIdle() {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
