package com.projectos.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NineRouterClientTest {
    private HttpServer server;
    private AtomicReference<String> authorization;
    private AtomicReference<String> requestBody;
    private boolean failModels;
    private NineRouterClient client;

    @BeforeEach
    void setUp() throws IOException {
        authorization = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/models", exchange -> {
            if (failModels) {
                exchange.sendResponseHeaders(401, 0);
                exchange.getResponseBody().close();
                return;
            }
            respond(exchange,
                    "{\"object\":\"list\",\"data\":[{\"id\":\"erp\",\"owned_by\":\"9router\"},{\"id\":\"ag/gemini\",\"owned_by\":\"provider\",\"kind\":\"chat\"}]}");
        });
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"id\":\"chat-1\",\"model\":\"erp\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}");
        });
        server.start();
        client = new NineRouterClient(RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(),
                new AiProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", Duration.ofSeconds(2), Duration.ofSeconds(2)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void discoversModelsFromNineRouter() {
        assertThat(client.listModels()).extracting(AiModelView::id).containsExactly("erp", "ag/gemini");
    }

    @Test
    void sendsOpenAiChatRequestWithBackendKey() {
        CompletionResult result = client.complete("erp", List.of(new ChatTurn("user", "Hi")));

        assertThat(result.content()).isEqualTo("Hello");
        assertThat(authorization).hasValue("Bearer test-key");
        assertThat(requestBody).hasValueSatisfying(body -> {
            assertThat(body).contains("\"model\":\"erp\"");
            assertThat(body).contains("\"content\":\"Hi\"");
        });
    }

    @Test
    void exposesUpstreamStatusForErrorMapping() {
        failModels = true;

        assertThatThrownBy(client::listModels)
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).status()).isEqualTo(401));
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
