package com.projectos.backend.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/ai")
public class AiController {
    private final AiConversationService service;

    public AiController(AiConversationService service) {
        this.service = service;
    }

    @GetMapping("/models")
    ApiResponse<List<AiConversationService.ModelView>> models(@PathVariable UUID organizationId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.models(organizationId, actor(jwt), root(jwt)));
    }

    @PostMapping("/connection-test")
    ApiResponse<AiConversationService.ConnectionTestView> testConnection(@PathVariable UUID organizationId,
                                                                          @Valid @RequestBody ConnectionTestRequest request,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.testConnection(organizationId, actor(jwt), root(jwt), request.modelId()));
    }

    @PostMapping("/completions")
    ApiResponse<CompletionResult> complete(@PathVariable UUID organizationId,
                                           @Valid @RequestBody CompletionRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.complete(organizationId, request.projectId(), actor(jwt), root(jwt),
                new AiConversationService.CompletionRequest(request.modelId(), request.messages().stream()
                        .map(message -> new AiConversationService.CompletionMessage(message.role(), message.content())).toList())));
    }

    @GetMapping("/conversations")
    PageResponse<AiConversationService.ConversationView> conversations(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, projectId, actor(jwt), root(jwt), page, size, search);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AiConversationService.ConversationView> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(organizationId, request.projectId(), actor(jwt), root(jwt),
                new AiConversationService.CreateConversationRequest(request.title(), request.modelId())));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    PageResponse<AiConversationService.MessageView> messages(
            @PathVariable UUID organizationId,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.messagePage(organizationId, conversationId, actor(jwt), root(jwt), page, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    ApiResponse<AiConversationService.MessageView> send(
            @PathVariable UUID organizationId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.send(organizationId, conversationId, actor(jwt), root(jwt),
                new AiConversationService.SendMessageRequest(request.content())));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }

    private boolean root(Jwt jwt) {
        return "ROOT_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    public record CreateConversationRequest(@Size(max = 200) String title,
                                            @NotBlank @Size(max = 200) String modelId,
                                            UUID projectId) {
    }

    public record CompletionRequest(@Size(max = 200) String modelId, UUID projectId,
                                    @jakarta.validation.constraints.NotEmpty @Size(max = 100) List<CompletionMessage> messages) {
    }

    public record CompletionMessage(@jakarta.validation.constraints.NotBlank String role,
                                    @jakarta.validation.constraints.NotBlank @Size(max = 20000) String content) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 20000) String content) {
    }

    public record ConnectionTestRequest(@Size(max = 200) String modelId) {
    }
}
