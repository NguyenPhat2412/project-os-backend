package vn.uytinmang.projectos.resource;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxDispatcher {
    private final OutboxEventRepository events;
    private final RestClient activity;
    private final String internalToken;

    OutboxDispatcher(OutboxEventRepository events,
                     @Value("${app.outbox.activity-url:http://localhost:8086}") String activityUrl,
                     @Value("${app.outbox.internal-token}") String internalToken) {
        this.events = events;
        this.activity = RestClient.builder().baseUrl(activityUrl).build();
        this.internalToken = internalToken;
    }

    @Scheduled(fixedDelayString = "${app.outbox.interval-ms:5000}")
    @Transactional
    void dispatch() {
        for (OutboxEvent event : events
                .findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now())) {
            try {
                activity.post().uri("/api/v1/internal/activities")
                        .header("X-Internal-Token", internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(event.getPayload())
                        .retrieve().toBodilessEntity();
                event.delivered();
            } catch (Exception exception) {
                event.failed(exception);
            }
        }
    }
}
