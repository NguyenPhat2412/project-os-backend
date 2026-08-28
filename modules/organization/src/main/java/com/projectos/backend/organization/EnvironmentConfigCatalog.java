package com.projectos.backend.organization;

import java.util.Set;

/**
 * The single environment contract exposed to the administrator settings API.
 * Local Compose-only and frontend-owned variables intentionally do not belong here.
 */
public final class EnvironmentConfigCatalog {
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "SERVER_PORT", "PUBLIC_WEB_URL", "CORS_ALLOWED_ORIGINS",
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "DB_POOL_SIZE",
            "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD", "REDIS_SSL",
            "JWT_SECRET", "JWT_TTL_HOURS", "INTERNAL_SERVICE_TOKEN", "COOKIE_SECURE",
            "RATE_LIMIT_ENABLED", "OPENAPI_ENABLED", "OPENAPI_PUBLIC",
            "NINEROUTER_URL", "NINEROUTER_KEY", "NINEROUTER_CONNECT_TIMEOUT", "NINEROUTER_READ_TIMEOUT",
            "OBJECT_STORAGE_ENDPOINT", "OBJECT_STORAGE_ACCESS_KEY", "OBJECT_STORAGE_SECRET_KEY", "OBJECT_STORAGE_BUCKET",
            "EMAIL_WORKER_ENABLED", "EMAIL_WORKER_INTERVAL_MS", "EMAIL_WORKER_BATCH_SIZE", "EMAIL_WORKER_MAX_ATTEMPTS",
            "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_CONNECT_TIMEOUT_MS", "SMTP_TIMEOUT_MS",
            "OUTBOX_ENABLED", "OUTBOX_INTERVAL_MS", "MAX_FILE_SIZE", "MAX_REQUEST_SIZE",
            "BOOTSTRAP_ADMIN_ENABLED", "BOOTSTRAP_ADMIN_EMAIL", "BOOTSTRAP_ADMIN_PASSWORD", "BOOTSTRAP_ADMIN_NAME",
            "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GOOGLE_OAUTH_REDIRECT_URI", "GOOGLE_OAUTH_SUCCESS_URL");

    public static final Set<String> SECRET_KEYS = Set.of(
            "DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN",
            "NINEROUTER_KEY", "OBJECT_STORAGE_ACCESS_KEY", "OBJECT_STORAGE_SECRET_KEY",
            "SMTP_PASSWORD", "GOOGLE_CLIENT_SECRET", "BOOTSTRAP_ADMIN_PASSWORD");

    public static final Set<String> URL_KEYS = Set.of(
            "PUBLIC_WEB_URL", "NINEROUTER_URL", "OBJECT_STORAGE_ENDPOINT",
            "GOOGLE_OAUTH_REDIRECT_URI", "GOOGLE_OAUTH_SUCCESS_URL");

    public static final Set<String> JDBC_URL_KEYS = Set.of("DB_URL");

    public static final Set<String> PORT_KEYS = Set.of("SERVER_PORT", "REDIS_PORT");

    private EnvironmentConfigCatalog() { }
}
