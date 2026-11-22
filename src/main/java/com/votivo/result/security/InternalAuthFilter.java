package com.votivo.result.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Result Service is reachable only through the gateway: the gateway verifies the user's token (and DPoP proof) and
 * decides who may read which poll, so a caller that reaches this service directly would skip the poll's access rules.
 * Every {@code /api} call must carry the shared secret in {@code X-Internal-Auth}. Health probes stay open.
 * The service refuses to start without the secret.
 */
@Component
public final class InternalAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Auth";
    private final byte[] secret;

    public InternalAuthFilter(@Value("${app.internal-auth-secret}") String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("INTERNAL_AUTH_SECRET must be set");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        // MessageDigest.isEqual compares in constant time for equal lengths; the length of a secret is not secret.
        if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
