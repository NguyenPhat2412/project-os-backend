package com.projectos.backend.platform.activity;

import tools.jackson.databind.JsonNode;

/** In-process port for durable activity publication. */
public interface ActivityPublisher {
    void publish(JsonNode payload);
}
