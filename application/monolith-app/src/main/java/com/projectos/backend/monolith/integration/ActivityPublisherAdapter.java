package com.projectos.backend.monolith.integration;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import com.projectos.backend.activity.ActivityPublisherService;
import com.projectos.backend.platform.activity.ActivityPublisher;

@Component
public class ActivityPublisherAdapter implements ActivityPublisher {
    private final ActivityPublisherService activities;

    public ActivityPublisherAdapter(ActivityPublisherService activities) {
        this.activities = activities;
    }

    @Override
    public void publish(JsonNode payload) {
        activities.publish(payload);
    }
}
