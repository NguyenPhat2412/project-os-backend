package com.projectos.backend.ai;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import com.projectos.backend.platform.api.ApiException;

final class AiModelPolicy {
    private AiModelPolicy() {}

    static List<AiModelView> visibleModels(List<AiModelView> available, Set<String> allowedModelIds) {
        if (allowedModelIds == null || allowedModelIds.isEmpty()) return available;
        return available.stream().filter(model -> allowedModelIds.contains(model.id())).toList();
    }

    static String requireSelectable(String requestedModel, List<AiModelView> available, Set<String> allowedModelIds) {
        String model = requestedModel == null ? "" : requestedModel.trim();
        if (model.isBlank() || available.stream().noneMatch(candidate -> model.equals(candidate.id()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ai_model_not_found", "The selected AI model is unavailable");
        }
        if (allowedModelIds != null && !allowedModelIds.isEmpty() && !allowedModelIds.contains(model)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ai_model_not_allowed", "The selected AI model is not enabled");
        }
        return model;
    }

    static Set<String> normalizeAllowedModelIds(Set<String> modelIds) {
        if (modelIds == null) return Set.of();
        return modelIds.stream().map(value -> value == null ? "" : value.trim()).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
}
