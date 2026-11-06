package com.aut172.maintenance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = AuthenticatedActor.class.getName();
    private final Environment environment;
    private final ObjectMapper json;

    public BearerAuthenticationFilter(Environment environment, ObjectMapper json) {
        this.environment = environment;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        request.setAttribute("traceId", traceId);
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, traceId);
            return;
        }
        String token = authorization.substring(7).trim();
        String identity = environment.getProperty("app.security.tokens." + token);
        if (identity == null || !identity.contains("|")) {
            unauthorized(response, traceId);
            return;
        }
        String[] parts = identity.split("\\|", 2);
        try {
            request.setAttribute(ACTOR_ATTRIBUTE,
                    new AuthenticatedActor(parts[0], AuthenticatedActor.Role.valueOf(parts[1])));
        } catch (IllegalArgumentException error) {
            unauthorized(response, traceId);
            return;
        }
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String traceId) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        json.writeValue(response.getWriter(), Map.of(
                "code", "UNAUTHENTICATED", "message", "Valid Bearer token required",
                "details", Map.of(), "traceId", traceId));
    }
}
