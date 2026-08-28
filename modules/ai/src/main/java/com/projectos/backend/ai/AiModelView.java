package com.projectos.backend.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiModelView(String id, @JsonProperty("owned_by") String ownedBy, String kind) {
}
