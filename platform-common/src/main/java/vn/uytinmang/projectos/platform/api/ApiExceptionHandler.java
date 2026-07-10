package vn.uytinmang.projectos.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> api(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception,
                                                HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "validation_failed", "Validation failed", fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable(HttpMessageNotReadableException exception,
                                                HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "invalid_body", "Invalid request body", Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> denied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "forbidden", "Access denied", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unhandled API error traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(new ApiError("internal_error", "Internal server error",
                        Map.of(), traceId)));
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
                                                      Map<String, String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(new ApiError(code, message, fields, traceId(request))));
    }

    private String traceId(HttpServletRequest request) {
        String incoming = request.getHeader("X-Request-ID");
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
    }

    public record ApiErrorResponse(ApiError error) {
    }

    public record ApiError(String code, String message, Map<String, String> fieldErrors, String traceId) {
    }
}
