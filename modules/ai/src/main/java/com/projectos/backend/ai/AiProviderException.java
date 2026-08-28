package com.projectos.backend.ai;

public class AiProviderException extends RuntimeException {
    private final int status;

    public AiProviderException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public AiProviderException(int status, String message) {
        this(status, message, null);
    }

    public int status() {
        return status;
    }
}
