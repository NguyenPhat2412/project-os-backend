package com.projectos.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.projectos.backend.platform.api.ApiException;

class AiModelPolicyTest {
    private final List<AiModelView> available = List.of(
            new AiModelView("model-a", "9router", "chat"),
            new AiModelView("model-b", "9router", "chat"));

    @Test
    void exposesAllProviderModelsWhenAdminHasNoAllowList() {
        assertThat(AiModelPolicy.visibleModels(available, Set.of())).containsExactlyElementsOf(available);
    }

    @Test
    void exposesOnlyModelsEnabledByAdmin() {
        assertThat(AiModelPolicy.visibleModels(available, Set.of("model-b")))
                .extracting(AiModelView::id).containsExactly("model-b");
    }

    @Test
    void rejectsUnavailableModel() {
        assertThatThrownBy(() -> AiModelPolicy.requireSelectable("missing", available, Set.of()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code()).isEqualTo("ai_model_not_found");
    }

    @Test
    void rejectsModelDisabledByAdmin() {
        assertThatThrownBy(() -> AiModelPolicy.requireSelectable("model-a", available, Set.of("model-b")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code()).isEqualTo("ai_model_not_allowed");
    }
}
