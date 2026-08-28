package com.projectos.backend.operations.email;

import static com.projectos.backend.operations.email.EmailCampaignContracts.*;
import com.projectos.backend.platform.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
public class EmailCampaignController {
    private final EmailCampaignService service;
    public EmailCampaignController(EmailCampaignService service) { this.service = service; }

    @GetMapping("/email-templates")
    ApiResponse<List<TemplateView>> templates(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.templates(organizationId, actor(jwt), root(jwt))); }
    @PostMapping("/email-templates") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<TemplateView> createTemplate(@PathVariable UUID organizationId, @RequestBody TemplateRequest request, @AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(service.createTemplate(organizationId,actor(jwt),root(jwt),request)); }
    @PatchMapping("/email-templates/{id}")
    ApiResponse<TemplateView> updateTemplate(@PathVariable UUID organizationId,@PathVariable UUID id,@RequestBody TemplateRequest request,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.updateTemplate(organizationId,id,actor(jwt),root(jwt),request));}
    @DeleteMapping("/email-templates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){service.deleteTemplate(organizationId,id,actor(jwt),root(jwt));}

    @PostMapping("/email-campaigns/preview")
    ApiResponse<PreviewResponse> preview(@PathVariable UUID organizationId,@RequestBody PreviewRequest request,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.preview(organizationId,actor(jwt),root(jwt),request));}
    @GetMapping("/email-campaigns")
    ApiResponse<List<CampaignView>> campaigns(@PathVariable UUID organizationId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.campaigns(organizationId,actor(jwt),root(jwt)));}
    @PostMapping("/email-campaigns") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CampaignView> createCampaign(@PathVariable UUID organizationId,@RequestBody CampaignRequest request,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.createCampaign(organizationId,actor(jwt),root(jwt),request));}
    @GetMapping("/email-campaigns/{id}")
    ApiResponse<CampaignView> campaign(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.getCampaign(organizationId,id,actor(jwt),root(jwt)));}
    @GetMapping("/email-campaigns/{id}/recipients")
    ApiResponse<List<Map<String,Object>>> recipients(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.campaignRecipients(organizationId,id,actor(jwt),root(jwt)));}
    @PostMapping("/email-campaigns/{id}/queue")
    ApiResponse<CampaignView> queue(@PathVariable UUID organizationId,@PathVariable UUID id,@RequestBody Map<String,String> body,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.queue(organizationId,id,actor(jwt),root(jwt),body.get("previewHash")));}
    @PostMapping("/email-campaigns/{id}/cancel")
    ApiResponse<CampaignView> cancel(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.cancel(organizationId,id,actor(jwt),root(jwt)));}
    @PostMapping("/email-campaigns/{id}/retry-failed")
    ApiResponse<CampaignView> retry(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.retryFailed(organizationId,id,actor(jwt),root(jwt)));}

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
