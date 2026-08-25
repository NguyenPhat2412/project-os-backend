package com.projectos.backend.platform.api;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {
    private final ObjectProvider<Tracer> tracer;
    private final ApiErrorCatalog catalog;

    public ApiErrorFactory(ObjectProvider<Tracer> tracer, ApiErrorCatalog catalog) {
        this.tracer = tracer;
        this.catalog = catalog;
    }

    public ApiExceptionHandler.ApiErrorResponse response(HttpStatus status, String code, String message,
                                                          Map<String, String> fields,
                                                          HttpServletRequest request) {
        ApiErrorCatalog.Descriptor descriptor = catalog.describe(status, code);
        return new ApiExceptionHandler.ApiErrorResponse(new ApiExceptionHandler.ApiError(code, descriptor.name(),
                descriptor.message(), fields,
                traceId(request), Instant.now(), status.value(), request.getRequestURI()));
    }

    private String traceId(HttpServletRequest request) {
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        if (span != null) return span.context().traceId();
        String incoming = request.getHeader("X-Request-ID");
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
    }
}
