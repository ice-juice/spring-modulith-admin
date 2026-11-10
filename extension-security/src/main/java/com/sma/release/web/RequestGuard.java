package com.sma.release.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolJson;
import com.sma.release.ReleaseProperties;
import com.sma.release.store.ReleaseDatabase;

/** Small chunk/JSON bodies only. Cross-origin browser requests and high-cost auth sprays fail before Argon2. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestGuard extends OncePerRequestFilter {
    private final ReleaseProperties properties;
    private final ReleaseDatabase database;
    public RequestGuard(ReleaseProperties properties, ReleaseDatabase database) { this.properties = properties; this.database = database; }
    public record RateWindow(long minute, int count) {}
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache"); response.setHeader("Referrer-Policy", "no-referrer");
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.equals(properties.origin())) { reject(response, 403, "origin-denied"); return; }
        if (request.getRequestURI().startsWith("/release/v1/auth/")) {
            String source = Hashes.sha256(request.getRemoteAddr().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean allowed = database.transaction(tx -> {
                long minute = tx.now().getEpochSecond()/60;
                var local = tx.get("rate", source, RateWindow.class).filter(r -> r.minute() == minute).orElse(new RateWindow(minute, 0));
                var global = tx.get("rate", "global", RateWindow.class).filter(r -> r.minute() == minute).orElse(new RateWindow(minute, 0));
                if (local.count() >= 30 || global.count() >= 300) return false;
                tx.put("rate", source, new RateWindow(minute, local.count()+1)); tx.put("rate", "global", new RateWindow(minute, global.count()+1)); return true;
            });
            if (!allowed) { reject(response, 429, "authentication-throttled"); return; }
        }
        int limit = 2 * 1024 * 1024;
        if (request.getContentLengthLong() > limit) { reject(response, 413, "request-too-large"); return; }
        byte[] body = request.getInputStream().readNBytes(limit+1);
        if (body.length > limit) { reject(response, 413, "request-too-large"); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int count) { return input.read(bytes, offset, count); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async request streaming is not supported"); }
                };
            }
        }, response);
    }
    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.getOutputStream().write(ProtocolJson.write(ApiReply.error(status, code)));
    }
}
