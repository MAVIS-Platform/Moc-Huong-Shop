package com.mochuong.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mochuong.gateway.security.JwtTokenVerifier;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces centralized JWT authentication at the edge and prevents downstream header spoofing.
 */
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenVerifier jwtTokenVerifier;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationGatewayFilter(JwtTokenVerifier jwtTokenVerifier, ObjectMapper objectMapper) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isPublicRoute(request)) {
            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(this::stripIdentityHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization Bearer header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        JwtTokenVerifier.VerifiedUser verifiedUser;
        try {
            verifiedUser = jwtTokenVerifier.verify(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "Invalid or expired JWT token");
        }

        ServerHttpRequest authenticatedRequest = request.mutate()
                .headers(headers -> {
                    stripIdentityHeaders(headers);
                    headers.set(USER_ID_HEADER, verifiedUser.userId());
                    headers.set(USER_EMAIL_HEADER, verifiedUser.email());
                    headers.set(USER_ROLE_HEADER, verifiedUser.role());
                })
                .build();

        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    private boolean isPublicRoute(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();

        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (path.startsWith("/actuator/health")) {
            return true;
        }
        if (HttpMethod.POST.equals(method)
                && ("/api/v1/auth/login".equals(path) || "/api/v1/auth/register".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method)
                && (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories"))) {
            return true;
        }
        return false;
    }

    private void stripIdentityHeaders(HttpHeaders headers) {
        headers.remove(USER_ID_HEADER);
        headers.remove(USER_EMAIL_HEADER);
        headers.remove(USER_ROLE_HEADER);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(exchange.getRequest().getURI().getPath()));

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException ex) {
            payload = ("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + detail + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(payload);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
