package com.projectos.backend.ai;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class NineRouterClient {
    private final RestClient restClient;
    private final AiProperties properties;

    public NineRouterClient(RestClient restClient, AiProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public List<AiModelView> listModels() {
        try {
            ModelListPayload payload = restClient.get()
                    .uri("/v1/models")
                    .header("Authorization", bearerToken())
                    .retrieve()
                    .body(ModelListPayload.class);
            return payload == null || payload.data() == null ? List.of() : payload.data();
        } catch (RestClientResponseException exception) {
            throw providerError(exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "9Router is unavailable", exception);
        }
    }

    public CompletionResult complete(String model, List<ChatTurn> messages) {
        try {
            CompletionPayload payload = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", bearerToken())
                    .body(new CompletionRequest(model, messages))
                    .retrieve()
                    .body(CompletionPayload.class);
            if (payload == null || payload.choices() == null || payload.choices().isEmpty()
                    || payload.choices().getFirst().message() == null) {
                throw new AiProviderException(502, "9Router returned an empty completion");
            }
            Choice choice = payload.choices().getFirst();
            Usage usage = payload.usage();
            return new CompletionResult(
                    choice.message().content(),
                    payload.model(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens());
        } catch (RestClientResponseException exception) {
            throw providerError(exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "9Router is unavailable", exception);
        }
    }

    private String bearerToken() {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new AiProviderException(503, "9Router API key is not configured");
        }
        return "Bearer " + properties.key();
    }

    private AiProviderException providerError(RestClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String body = exception.getResponseBodyAsString();
        String message = body == null || body.isBlank() ? exception.getStatusText() : body;
        return new AiProviderException(statusCode.value(), message, exception);
    }

    private record ModelListPayload(List<AiModelView> data) {
    }

    private record CompletionRequest(String model, List<ChatTurn> messages) {
    }

    private record CompletionPayload(String model, List<Choice> choices, Usage usage) {
    }

    private record Choice(Message message) {
    }

    private record Message(String role, String content) {
    }

    private record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                         @JsonProperty("completion_tokens") Integer completionTokens) {
    }
}
