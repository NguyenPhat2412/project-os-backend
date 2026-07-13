package vn.uytinmang.projectos.resource;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id private UUID id;
    @Column(name = "event_type", nullable = false, length = 120) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private JsonNode payload;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "delivered_at") private Instant deliveredAt;

    protected OutboxEvent() {
    }

    OutboxEvent(String eventType, JsonNode payload) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    void delivered() {
        deliveredAt = Instant.now();
        lastError = null;
    }

    void failed(Exception exception) {
        attempts++;
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        nextAttemptAt = Instant.now().plus(delaySeconds, ChronoUnit.SECONDS);
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        lastError = message.length() > 500 ? message.substring(0, 500) : message;
    }

    JsonNode getPayload() { return payload; }
}
