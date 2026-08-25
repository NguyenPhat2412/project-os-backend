package com.projectos.backend.resource;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.projectos.backend.platform.activity.ActivityPublisher;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxDispatcher {
    private final OutboxEventRepository events;
    private final ActivityPublisher activity;

    OutboxDispatcher(OutboxEventRepository events, ObjectProvider<ActivityPublisher> activity) {
        this.events = events;
        this.activity = activity.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${app.outbox.interval-ms:5000}")
    @Transactional
    void dispatch() {
        for (OutboxEvent event : events
                .findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now())) {
            try {
                if (activity == null) throw new IllegalStateException("Activity publisher port is unavailable");
                activity.publish(event.getPayload());
                event.delivered();
            } catch (Exception exception) {
                event.failed(exception);
            }
        }
    }
}
