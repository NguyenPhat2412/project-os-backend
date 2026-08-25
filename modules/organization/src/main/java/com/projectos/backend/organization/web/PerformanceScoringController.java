package com.projectos.backend.organization.web;

import com.projectos.backend.organization.domain.PerformanceScoringService;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
public class PerformanceScoringController {
    private final PerformanceScoringService service;

    public PerformanceScoringController(PerformanceScoringService service) {
        this.service = service;
    }

    @GetMapping("/scoring-rules")
    PageResponse<ScoringRuleView> rules(@PathVariable UUID organizationId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "false") boolean activeOnly,
                                        @RequestParam(defaultValue = "") String search,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.listRules(organizationId, page, size, activeOnly, search, actor(jwt), root(jwt));
    }

    @PostMapping("/scoring-rules")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ScoringRuleView> createRule(@PathVariable UUID organizationId,
                                            @Valid @RequestBody ScoringRuleRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createRule(organizationId, request, actor(jwt), root(jwt)));
    }

    @PatchMapping("/scoring-rules/{ruleId}")
    ApiResponse<ScoringRuleView> updateRule(@PathVariable UUID organizationId, @PathVariable UUID ruleId,
                                            @Valid @RequestBody ScoringRulePatch request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.updateRule(organizationId, ruleId, request, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/scoring-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivateRule(@PathVariable UUID organizationId, @PathVariable UUID ruleId,
                        @AuthenticationPrincipal Jwt jwt) {
        service.deactivateRule(organizationId, ruleId, actor(jwt), root(jwt));
    }

    @GetMapping("/score-events")
    PageResponse<ScoreEventView> events(@PathVariable UUID organizationId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) UUID employeeId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(defaultValue = "") String search,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.listEvents(organizationId, page, size, employeeId, from, to, search, actor(jwt), root(jwt));
    }

    @PostMapping("/score-events")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ScoreEventView> createEvent(@PathVariable UUID organizationId,
                                            @Valid @RequestBody ScoreEventRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.createEvent(organizationId, request, actor(jwt), root(jwt)));
    }

    @GetMapping("/scoreboard")
    PageResponse<ScoreboardRow> scoreboard(@PathVariable UUID organizationId,
                                           @RequestParam(required = false) String periodFrom,
                                           @RequestParam(required = false) String periodTo,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @AuthenticationPrincipal Jwt jwt) {
        return service.scoreboard(organizationId, page, size, periodFrom, periodTo, actor(jwt), root(jwt));
    }

    @GetMapping("/score-summary")
    ApiResponse<ScoreSummary> summary(@PathVariable UUID organizationId,
                                      @RequestParam String employeeCode,
                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.summary(organizationId, employeeCode, actor(jwt), root(jwt)));
    }

    @PostMapping("/scoring-rules/points")
    ApiResponse<PointAwardResult> awardPoints(@PathVariable UUID organizationId,
                                              @Valid @RequestBody PointAwardRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.awardCompatibility(organizationId, request, actor(jwt), root(jwt)));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private static boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record ScoringRuleRequest(@NotBlank @Size(max = 80) String ruleCode,
                                     @NotBlank @Size(max = 200) String name,
                                     @Size(max = 4000) String description,
                                     @NotBlank @Size(max = 80) String category,
                                     @NotNull Integer points,
                                     Boolean isActive) {}

    public record ScoringRulePatch(@Size(max = 80) String ruleCode,
                                   @Size(max = 200) String name,
                                   @Size(max = 4000) String description,
                                   @Size(max = 80) String category,
                                   Integer points,
                                   Boolean isActive) {}

    public record ScoreEventRequest(@NotNull UUID employeeId,
                                    @Size(max = 80) String ruleCode,
                                    @Size(max = 80) String source,
                                    @Size(max = 180) String eventKey,
                                    Integer points,
                                    @NotBlank @Size(max = 1000) String reason,
                                    Instant occurredAt) {}

    public record PointAwardRequest(@NotBlank @Size(max = 80) String employeeCode,
                                    @NotBlank @Size(max = 80) String ruleCode,
                                    @Size(max = 80) String source,
                                    @Size(max = 180) String eventKey,
                                    @NotBlank @Size(max = 1000) String reason) {}

    public record ScoringRuleView(UUID id, UUID organizationId, String ruleCode, String name, String description,
                                  String category, int points, boolean isActive, Instant createdAt, Instant updatedAt) {}

    public record ScoreEventView(UUID id, UUID organizationId, UUID employeeId, String employeeCode,
                                 String employeeName, String ruleCode, String source, String eventKey,
                                 int points, String reason, Instant occurredAt, Instant createdAt) {}

    public record ScoreboardRow(long rank, UUID employeeId, String employeeCode, String employeeName,
                                java.math.BigDecimal kpiScore, int eventPoints, java.math.BigDecimal totalScore) {}

    public record ScoreSummary(UUID employeeId, String employeeCode, String employeeName,
                               int totalPoints, String tier) {}

    public record PointAwardResult(UUID eventId, int pointsAwarded, boolean created) {}
}
