package com.projectos.backend.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.api.PageResponse;
import com.projectos.backend.platform.organization.AiConfiguration;
import com.projectos.backend.platform.organization.OrganizationDirectory;
import com.projectos.backend.platform.organization.OrganizationPermissionPort;
import com.projectos.backend.platform.project.ProjectAccessPort;
import com.projectos.backend.platform.project.ProjectPermissionChecker;

@Service
public class AiConversationService {
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final NineRouterClient router;
    private final ObjectProvider<OrganizationDirectory> organizations;
    private final ObjectProvider<ProjectPermissionChecker> projectPermissions;
    private final ObjectProvider<ProjectAccessPort> projectAccess;
    private final ObjectProvider<OrganizationPermissionPort> organizationPermissions;

    public AiConversationService(AiConversationRepository conversations, AiMessageRepository messages,
                                 NineRouterClient router, ObjectProvider<OrganizationDirectory> organizations,
                                 ObjectProvider<ProjectPermissionChecker> projectPermissions,
                                 ObjectProvider<ProjectAccessPort> projectAccess,
                                 ObjectProvider<OrganizationPermissionPort> organizationPermissions) {
        this.conversations = conversations;
        this.messages = messages;
        this.router = router;
        this.organizations = organizations;
        this.projectPermissions = projectPermissions;
        this.projectAccess = projectAccess;
        this.organizationPermissions = organizationPermissions;
    }

    @Transactional(readOnly = true)
    public List<ModelView> models(UUID organizationId, UUID actorId, boolean root) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        requireAiPermission(organizationId, actorId, root, "component:ai:model-read");
        authorize(organizationId, null, actorId, root);
        return availableModels(organizationId);
    }

    @Transactional(readOnly = true)
    public ConnectionTestView testConnection(UUID organizationId, UUID actorId, boolean root, String requestedModel) {
        if (!root) {
            throw new ApiException(HttpStatus.FORBIDDEN, "root_admin_required",
                    "Chỉ quản trị cấp cao mới được kiểm tra kết nối Trợ lý.");
        }
        requireAiPermission(organizationId, actorId, root, "page:ai");
        requireAiPermission(organizationId, actorId, root, "component:ai:provider-test");
        AiConfiguration configuration = aiConfiguration(organizationId);
        List<AiModelView> available = availableProviderModels();
        String model = requestedModel == null || requestedModel.isBlank()
                ? configuration.defaultModelId()
                : requestedModel.trim();
        if (model.isBlank()) {
            model = AiModelPolicy.visibleModels(available, configuration.allowedModelIds()).stream()
                    .findFirst().map(AiModelView::id).orElseThrow(() -> new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE, "ai_model_unavailable",
                            "Hiện chưa có mô hình AI khả dụng để kiểm tra."));
        }
        String selectable = AiModelPolicy.requireSelectable(model, available, configuration.allowedModelIds());
        AiModelView selected = available.stream().filter(item -> item.id().equals(selectable)).findFirst().orElseThrow();
        return new ConnectionTestView(true, selected.id(), AiModelFamily.from(selected).name());
    }

    private List<ModelView> availableModels(UUID organizationId) {
        return AiModelPolicy.visibleModels(availableProviderModels(), aiConfiguration(organizationId).allowedModelIds()).stream()
                .map(model -> new ModelView(model.id(), model.ownedBy(), model.kind(), AiModelFamily.from(model).name())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> list(UUID organizationId, UUID projectId, UUID actorId, boolean root,
                                               int page, int size, String search) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        authorize(organizationId, projectId, actorId, root);
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<AiConversation> result;
        if (projectId == null && normalizedSearch.isBlank()) {
            result = conversations.findByOrganizationIdAndOwnerUserIdOrderByUpdatedAtDesc(organizationId, actorId, request);
        } else if (projectId == null) {
            result = conversations.findByOrganizationIdAndOwnerUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                    organizationId, actorId, normalizedSearch, request);
        } else if (normalizedSearch.isBlank()) {
            result = conversations.findByOrganizationIdAndOwnerUserIdAndProjectIdOrderByUpdatedAtDesc(
                    organizationId, actorId, projectId, request);
        } else {
            result = conversations.findByOrganizationIdAndOwnerUserIdAndProjectIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                    organizationId, actorId, projectId, normalizedSearch, request);
        }
        return PageResponse.of(result.getContent().stream().map(this::conversationView).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ConversationView create(UUID organizationId, UUID projectId, UUID actorId, boolean root,
                                   CreateConversationRequest request) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        authorize(organizationId, projectId, actorId, root);
        String model = required(request.modelId(), "modelId");
        ensureModel(organizationId, model);
        String title = request.title() == null || request.title().isBlank() ? "New AI conversation" : request.title().trim();
        if (title.length() > 200) title = title.substring(0, 200);
        return conversationView(conversations.save(new AiConversation(organizationId, projectId, actorId, title, model)));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageView> messagePage(UUID organizationId, UUID conversationId, UUID actorId, boolean root,
                                                  int page, int size) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        AiConversation conversation = scopedConversation(organizationId, conversationId, actorId, root);
        Page<AiMessage> result = messages.findByConversationIdAndOrganizationIdAndOwnerUserIdOrderByCreatedAtAsc(
                conversation.getId(), organizationId, actorId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
        return PageResponse.of(result.getContent().stream().map(this::messageView).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public MessageView send(UUID organizationId, UUID conversationId, UUID actorId, boolean root,
                            SendMessageRequest request) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        requireAiPermission(organizationId, actorId, root, "component:ai:website-guide");
        AiConversation conversation = scopedConversation(organizationId, conversationId, actorId, root);
        ensureModel(organizationId, conversation.getModelId());
        String content = required(request.content(), "content");
        if (content.length() > 20000) throw new ApiException(HttpStatus.BAD_REQUEST, "content_too_long", "Message is too long");
        List<ChatTurn> history = messages.findByConversationIdAndOrganizationIdAndOwnerUserIdOrderByCreatedAtAsc(
                        conversationId, organizationId, actorId).stream()
                .map(message -> new ChatTurn(message.getRole() == AiMessage.Role.USER ? "user" : "assistant", message.getContent()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        messages.save(new AiMessage(conversationId, organizationId, actorId, AiMessage.Role.USER, content, conversation.getModelId()));
        history.add(new ChatTurn("user", content));
        CompletionResult completion;
        try {
            completion = router.complete(conversation.getModelId(), history);
        } catch (AiProviderException exception) {
            throw providerUnavailable(exception);
        }
        AiMessage assistant = messages.save(new AiMessage(conversationId, organizationId, actorId,
                AiMessage.Role.ASSISTANT, completion.content(), completion.model()));
        assistant.setUsage(completion.promptTokens(), completion.completionTokens());
        conversations.save(conversation);
        return messageView(assistant);
    }

    @Transactional(readOnly = true)
    public CompletionResult complete(UUID organizationId, UUID projectId, UUID actorId, boolean root,
                                     CompletionRequest request) {
        requireAiPermission(organizationId, actorId, root, "page:ai");
        requireAiPermission(organizationId, actorId, root, "component:ai:website-guide");
        authorize(organizationId, projectId, actorId, root);
        List<ChatTurn> turns = request.messages().stream()
                .map(message -> new ChatTurn(message.role(), message.content().trim()))
                .toList();
        if (turns.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "messages_required", "At least one message is required");
        String model = request.modelId() == null || request.modelId().isBlank()
                ? availableModels(organizationId).stream().findFirst().map(ModelView::id).orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, "ai_model_unavailable", "No AI model is available"))
                : request.modelId().trim();
        ensureModel(organizationId, model);
        try {
            return router.complete(model, turns);
        } catch (AiProviderException exception) {
            throw providerUnavailable(exception);
        }
    }

    private void ensureModel(UUID organizationId, String model) {
        List<AiModelView> available = availableProviderModels();
        AiModelPolicy.requireSelectable(model, available, aiConfiguration(organizationId).allowedModelIds());
    }

    private List<AiModelView> availableProviderModels() {
        try {
            return router.listModels().stream().map(item -> new AiModelView(item.id(), item.ownedBy(), item.kind())).toList();
        } catch (AiProviderException exception) {
            throw providerUnavailable(exception);
        }
    }

    private AiConfiguration aiConfiguration(UUID organizationId) {
        OrganizationDirectory directory = organizations.getIfAvailable();
        if (directory == null) return new AiConfiguration(Set.of(), "");
        AiConfiguration configuration = directory.aiConfiguration(organizationId);
        return configuration == null ? new AiConfiguration(Set.of(), "") : configuration;
    }

    private void authorize(UUID organizationId, UUID projectId, UUID actorId, boolean root) {
        if (!root) {
            OrganizationDirectory directory = organizations.getIfAvailable();
            if (directory == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_service_unavailable",
                    "Organization directory is unavailable");
            OrganizationDirectory.Access access = directory.access(organizationId, actorId);
            if (access == null || access.role() == null || access.role().isBlank()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "organization_access_denied", "Organization access denied");
            }
        }
        if (projectId != null) {
            ProjectAccessPort accessPort = projectAccess.getIfAvailable();
            if (accessPort == null || accessPort.organizationId(projectId).filter(organizationId::equals).isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "project_not_found", "Project was not found in this organization");
            }
            if (!root) {
                ProjectPermissionChecker checker = projectPermissions.getIfAvailable();
                if (checker == null || !checker.allowed(projectId, actorId, "projects", "read")) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "project_access_denied", "Project access denied");
                }
            }
        }
    }

    private void requireAiPermission(UUID organizationId, UUID actorId, boolean root, String permissionKey) {
        OrganizationPermissionPort permissionPort = organizationPermissions.getIfAvailable();
        if (permissionPort == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "organization_permission_unavailable",
                    "Organization permission service is unavailable");
        }
        permissionPort.requirePermission(organizationId, actorId, root, permissionKey);
    }

    private AiConversation scopedConversation(UUID organizationId, UUID conversationId, UUID actorId, boolean root) {
        AiConversation conversation = root
                ? conversations.findById(conversationId).filter(item -> organizationId.equals(item.getOrganizationId())).orElse(null)
                : conversations.findByIdAndOrganizationIdAndOwnerUserId(conversationId, organizationId, actorId).orElse(null);
        if (conversation == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "conversation_not_found", "Conversation was not found");
        }
        authorize(organizationId, conversation.getProjectId(), actorId, root);
        return conversation;
    }

    private ConversationView conversationView(AiConversation conversation) {
        return new ConversationView(conversation.getId(), conversation.getOrganizationId(), conversation.getProjectId(),
                conversation.getOwnerUserId(), conversation.getTitle(), conversation.getModelId(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private MessageView messageView(AiMessage message) {
        return new MessageView(message.getId(), message.getConversationId(), message.getRole().name(), message.getContent(),
                message.getProviderModel(), message.getPromptTokens(), message.getCompletionTokens(), message.getCreatedAt());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", field + " is required");
        return value.trim();
    }

    private ApiException providerUnavailable(AiProviderException exception) {
        int status = exception.status() == 429 ? 429 : HttpStatus.SERVICE_UNAVAILABLE.value();
        return new ApiException(HttpStatus.valueOf(status), "ai_provider_unavailable", "AI service is temporarily unavailable");
    }

    public record CreateConversationRequest(String title, String modelId) {}
    public record SendMessageRequest(String content) {}
    public record CompletionRequest(String modelId, List<CompletionMessage> messages) {}
    public record CompletionMessage(String role, String content) {}
    public record ModelView(String id, String ownedBy, String kind, String family) {}
    public record ConnectionTestView(boolean success, String modelId, String family) {}
    public record ConversationView(UUID id, UUID organizationId, UUID projectId, UUID ownerUserId, String title,
                                   String modelId, Instant createdAt, Instant updatedAt) {}
    public record MessageView(UUID id, UUID conversationId, String role, String content, String providerModel,
                              Integer promptTokens, Integer completionTokens, Instant createdAt) {}
}
