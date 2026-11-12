package com.sma.admin.common.internal.deployment;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.ErrorResponseWriter;

/** Registered before tenant lookup, body budgets, authentication and all business side effects. */
final class DeploymentTrafficFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeploymentTrafficFilter.class);
    private static final Set<String> PROBES = Set.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness");
    private final DeploymentTrafficGate gate;
    private final ErrorResponseWriter errors;

    DeploymentTrafficFilter(DeploymentTrafficGate gate, ErrorResponseWriter errors) { this.gate = gate; this.errors = errors; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) return false;
        return PROBES.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var state = gate.status();
        if (!state.accepts()) {
            log.debug("Deployment traffic refused: {}", state.state()); // Fixed state only; no request or identity data.
            response.setHeader("Cache-Control", "no-store");
            // No Retry-After or replay instruction: a 503 cannot settle a previous UNKNOWN business write.
            errors.write(request, response, CommonErrorCode.SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response); // Already admitted work may finish; no post-execution rollback/retry.
    }
}
