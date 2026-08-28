package com.projectos.backend.platform.organization;

import java.util.Set;

/** Organization-owned AI policy exposed to domain modules through an in-process port. */
public record AiConfiguration(Set<String> allowedModelIds, String defaultModelId) {
    public AiConfiguration {
        allowedModelIds = allowedModelIds == null ? Set.of() : Set.copyOf(allowedModelIds);
        defaultModelId = defaultModelId == null ? "" : defaultModelId;
    }
}
