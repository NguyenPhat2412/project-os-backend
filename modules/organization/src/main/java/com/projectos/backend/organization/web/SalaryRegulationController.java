package com.projectos.backend.organization.web;

import com.projectos.backend.organization.domain.SalaryRegulationService;
import com.projectos.backend.platform.api.ApiResponse;
import com.projectos.backend.platform.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/organizations/{organizationId}/salary-regulations")
public class SalaryRegulationController {
    private final SalaryRegulationService service;

    public SalaryRegulationController(SalaryRegulationService service) { this.service = service; }

    @GetMapping
    PageResponse<SalaryRegulationView> list(@PathVariable UUID organizationId, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "") String search,
                                           @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String salaryType,
                                           @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, page, size, search, status, salaryType, actor(jwt), root(jwt));
    }

    @GetMapping("/categories")
    ApiResponse<List<String>> categories(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.categories(organizationId, actor(jwt), root(jwt)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SalaryRegulationView> create(@PathVariable UUID organizationId, @Valid @RequestBody SalaryRegulationRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.create(organizationId, request, actor(jwt), root(jwt)));
    }

    @PatchMapping("/{id}")
    ApiResponse<SalaryRegulationView> update(@PathVariable UUID organizationId, @PathVariable UUID id,
                                             @Valid @RequestBody SalaryRegulationPatch request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(service.update(organizationId, id, request, actor(jwt), root(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID organizationId, @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(organizationId, id, actor(jwt), root(jwt));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private static boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }

    public record SalaryRegulationRequest(@NotBlank @Size(max = 80) String ruleCode, @NotBlank @Size(max = 200) String name,
                                          @NotBlank @Size(max = 100) String salaryType, @Size(max = 80) String gradeStep,
                                          @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal coefficient,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minAmount,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal maxAmount,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal baseSalary,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal titleSalary,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal performanceSalary,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal concurrentAllowance,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal gasolineAllowance,
                                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal otherAllowance,
                                          @NotNull LocalDate effectiveDate, @Size(max = 20) String status, @Size(max = 4000) String notes) {}

    public record SalaryRegulationPatch(@Size(max = 80) String ruleCode, @Size(max = 200) String name, @Size(max = 100) String salaryType,
                                        @Size(max = 80) String gradeStep, @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal coefficient,
                                        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minAmount, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal maxAmount,
                                        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal baseSalary, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal titleSalary,
                                        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal performanceSalary, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal concurrentAllowance,
                                        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal gasolineAllowance, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal otherAllowance,
                                        LocalDate effectiveDate, @Size(max = 20) String status, @Size(max = 4000) String notes) {}

    public record SalaryRegulationView(UUID id, UUID organizationId, String ruleCode, String name, String salaryType, String gradeStep,
                                       BigDecimal coefficient, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal baseSalary,
                                       BigDecimal titleSalary, BigDecimal performanceSalary, BigDecimal concurrentAllowance,
                                       BigDecimal gasolineAllowance, BigDecimal otherAllowance, BigDecimal totalSalary, LocalDate effectiveDate,
                                       String status, String notes, UUID createdBy, Instant createdAt, Instant updatedAt) {}
}
