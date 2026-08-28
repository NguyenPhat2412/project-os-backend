package com.projectos.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import com.projectos.backend.platform.organization.OrganizationDirectory;
import com.projectos.backend.platform.organization.OrganizationPermissionPort;
import com.projectos.backend.platform.organization.AiConfiguration;
import com.projectos.backend.platform.project.ProjectPermissionChecker;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {
    @Mock AiConversationRepository conversations;
    @Mock AiMessageRepository messages;
    @Mock NineRouterClient router;
    @Mock OrganizationDirectory organization;
    @Mock OrganizationPermissionPort organizationPermissions;
    @Mock ProjectPermissionChecker projectPermissions;
    @Mock com.projectos.backend.platform.project.ProjectAccessPort projectAccess;
    @Mock ObjectProvider<OrganizationDirectory> organizationProvider;
    @Mock ObjectProvider<OrganizationPermissionPort> organizationPermissionProvider;
    @Mock ObjectProvider<ProjectPermissionChecker> projectPermissionProvider;
    @Mock ObjectProvider<com.projectos.backend.platform.project.ProjectAccessPort> projectAccessProvider;
    private AiConversationService service;

    @BeforeEach
    void setUp() {
        lenient().when(organizationProvider.getIfAvailable()).thenReturn(organization);
        lenient().when(organizationPermissionProvider.getIfAvailable()).thenReturn(organizationPermissions);
        service = new AiConversationService(conversations, messages, router, organizationProvider,
                projectPermissionProvider, projectAccessProvider, organizationPermissionProvider);
    }

    @Test
    void createsConversationOnlyForOrganizationMember() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(organization.access(organizationId, userId))
                .thenReturn(new OrganizationDirectory.Access("Asia/Ho_Chi_Minh", "USER"));
        when(router.listModels()).thenReturn(java.util.List.of(
                new AiModelView("erp", "9router", "chat")));
        when(conversations.save(any(AiConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiConversationService.ConversationView view = service.create(organizationId, null, userId, false,
                new AiConversationService.CreateConversationRequest("Support", "erp"));

        assertThat(view.organizationId()).isEqualTo(organizationId);
        assertThat(view.ownerUserId()).isEqualTo(userId);
        assertThat(view.title()).isEqualTo("Support");
        verify(organizationPermissions).requirePermission(organizationId, userId, false, "page:ai");
    }

    @Test
    void exposesProviderFamilyForLiveModelPicker() {
        UUID organizationId = UUID.randomUUID();
        UUID rootUserId = UUID.randomUUID();
        when(router.listModels()).thenReturn(java.util.List.of(
                new AiModelView("gemini-2.5-flash", "google", "chat"),
                new AiModelView("gpt-5", "openai", "chat"),
                new AiModelView("claude-sonnet", "anthropic", "chat")));

        var models = service.models(organizationId, rootUserId, true);

        assertThat(models).extracting(AiConversationService.ModelView::family)
                .containsExactly("GEMINI", "GPT", "OTHER");
    }

    @Test
    void failsClosedWhenOrganizationPermissionPortIsUnavailable() {
        when(organizationPermissionProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.models(UUID.randomUUID(), UUID.randomUUID(), false))
                .isInstanceOf(com.projectos.backend.platform.api.ApiException.class)
                .extracting(exception -> ((com.projectos.backend.platform.api.ApiException) exception).code())
                .isEqualTo("organization_permission_unavailable");
    }

    @Test
    void sendsScopedHistoryToRouterAndPersistsAssistantReply() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AiConversation conversation = new AiConversation(organizationId, null, userId, "Chat", "erp");
        when(conversations.findByIdAndOrganizationIdAndOwnerUserId(conversationId, organizationId, userId))
                .thenReturn(Optional.of(conversation));
        when(organization.access(organizationId, userId))
                .thenReturn(new OrganizationDirectory.Access("Asia/Ho_Chi_Minh", "USER"));
        when(messages.findByConversationIdAndOrganizationIdAndOwnerUserIdOrderByCreatedAtAsc(conversationId,
                organizationId, userId)).thenReturn(java.util.List.of());
        when(router.listModels()).thenReturn(java.util.List.of(new AiModelView("erp", "9router", "chat")));
        when(messages.save(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(router.complete("erp", java.util.List.of(new ChatTurn("user", "Hello"))))
                .thenReturn(new CompletionResult("Hi there", "erp", 2, 3));

        var view = service.send(organizationId, conversationId, userId, false,
                new AiConversationService.SendMessageRequest("Hello"));

        assertThat(view.content()).isEqualTo("Hi there");
        assertThat(view.role()).isEqualTo("ASSISTANT");
    }

    @Test
    void rejectsExistingConversationWhenItsModelIsDisabledByAdmin() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AiConversation conversation = new AiConversation(organizationId, null, userId, "Chat", "erp");
        when(conversations.findByIdAndOrganizationIdAndOwnerUserId(conversationId, organizationId, userId))
                .thenReturn(Optional.of(conversation));
        when(organization.access(organizationId, userId))
                .thenReturn(new OrganizationDirectory.Access("Asia/Ho_Chi_Minh", "USER"));
        when(organization.aiConfiguration(organizationId)).thenReturn(new AiConfiguration(Set.of("other"), "other"));
        when(router.listModels()).thenReturn(java.util.List.of(new AiModelView("erp", "9router", "chat")));

        assertThatThrownBy(() -> service.send(organizationId, conversationId, userId, false,
                new AiConversationService.SendMessageRequest("Hello")))
                .isInstanceOf(com.projectos.backend.platform.api.ApiException.class)
                .extracting(exception -> ((com.projectos.backend.platform.api.ApiException) exception).code())
                .isEqualTo("ai_model_not_allowed");
    }

    @Test
    void testsTheLiveModelConnectionUsingTheOrganizationPolicy() {
        UUID organizationId = UUID.randomUUID();
        UUID rootUserId = UUID.randomUUID();
        when(organization.aiConfiguration(organizationId)).thenReturn(new AiConfiguration(Set.of("gpt-5"), "gpt-5"));
        when(router.listModels()).thenReturn(java.util.List.of(new AiModelView("gpt-5", "openai", "chat")));

        var result = service.testConnection(organizationId, rootUserId, true, "gpt-5");

        assertThat(result.success()).isTrue();
        assertThat(result.modelId()).isEqualTo("gpt-5");
    }
}
