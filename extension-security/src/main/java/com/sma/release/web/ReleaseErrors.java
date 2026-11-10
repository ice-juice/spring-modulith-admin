package com.sma.release.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.sma.extension.protocol.ProtocolException;

@RestControllerAdvice
public class ReleaseErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseErrors.class);
    @ExceptionHandler(ProtocolException.class) ResponseEntity<ApiReply<Void>> protocol(ProtocolException failure) {
        String code = failure.code();
        int status = code.equals("unauthenticated") || code.equals("authentication-failed") ? 401
                : code.equals("forbidden") ? 403 : code.equals("authentication-throttled") ? 429
                : code.endsWith("not-found") ? 404 : code.contains("conflict") ? 409 : 400;
        return ResponseEntity.status(status).body(ApiReply.error(status, code));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiReply<Void>> malformed(Exception failure) { return ResponseEntity.badRequest().body(ApiReply.error(400, "invalid-request")); }
    @ExceptionHandler(Exception.class) ResponseEntity<ApiReply<Void>> unknown(Exception failure) {
        // Do not log exception messages: parsers/JDBC can include credentials or command JSON in them.
        LOG.error("Release request failed: {}", failure.getClass().getName());
        return ResponseEntity.internalServerError().body(ApiReply.error(500, "internal-error"));
    }
}
