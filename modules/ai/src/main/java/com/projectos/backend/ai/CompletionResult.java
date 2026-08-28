package com.projectos.backend.ai;

public record CompletionResult(String content, String model, Integer promptTokens, Integer completionTokens) {
}
