package vn.uytinmang.projectos.resource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.uytinmang.projectos.platform.api.ApiResponse;
import vn.uytinmang.projectos.platform.api.PageResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ResourceController {
    private final ResourceApplicationService resources;

    public ResourceController(ResourceApplicationService resources) {
        this.resources = resources;
    }

    @GetMapping("/{resource}")
    PageResponse<JsonNode> list(@PathVariable UUID projectId, @PathVariable String resource,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "100") int size) {
        return resources.list(projectId, resource, page, size);
    }

    @GetMapping("/{resource}/{id}")
    ApiResponse<JsonNode> get(@PathVariable UUID projectId, @PathVariable String resource,
                              @PathVariable String id) {
        return ApiResponse.of(resources.get(projectId, resource, id));
    }

    @PostMapping("/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JsonNode> create(@PathVariable UUID projectId, @PathVariable String resource,
                                 @RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.createMutable(projectId, resource, body, actor(jwt)));
    }

    @PutMapping("/{resource}/{id}")
    ApiResponse<JsonNode> put(@PathVariable UUID projectId, @PathVariable String resource,
                              @PathVariable String id, @RequestBody JsonNode body,
                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.put(projectId, resource, id, body, actor(jwt)));
    }

    @PatchMapping("/{resource}/{id}")
    ApiResponse<JsonNode> patch(@PathVariable UUID projectId, @PathVariable String resource,
                                @PathVariable String id, @RequestBody JsonNode body,
                                @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.patch(projectId, resource, id, body, actor(jwt)));
    }

    @DeleteMapping("/{resource}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID projectId, @PathVariable String resource, @PathVariable String id,
                @AuthenticationPrincipal Jwt jwt) {
        resources.delete(projectId, resource, id, actor(jwt));
    }

    @PostMapping("/{resource}/reorder")
    ApiResponse<List<JsonNode>> reorder(@PathVariable UUID projectId, @PathVariable String resource,
                                        @RequestBody JsonNode body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.reorder(projectId, resource, body, actor(jwt)));
    }

    @GetMapping("/{parentResource}/{parentId}/comments")
    PageResponse<JsonNode> nestedComments(@PathVariable UUID projectId,
                                          @PathVariable String parentResource,
                                          @PathVariable String parentId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "100") int size) {
        return resources.list(projectId, nestedResource(parentResource, parentId), page, size);
    }

    @PostMapping("/{parentResource}/{parentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<JsonNode> createNestedComment(@PathVariable UUID projectId,
                                              @PathVariable String parentResource,
                                              @PathVariable String parentId,
                                              @RequestBody ObjectNode body,
                                              @AuthenticationPrincipal Jwt jwt) {
        body.put("parentResource", parentResource);
        body.put("parentId", parentId);
        return ApiResponse.of(resources.createMutable(projectId, nestedResource(parentResource, parentId), body,
                actor(jwt)));
    }

    @GetMapping("/{parentResource}/{parentId}/comments/{commentId}")
    ApiResponse<JsonNode> nestedComment(@PathVariable UUID projectId,
                                        @PathVariable String parentResource,
                                        @PathVariable String parentId,
                                        @PathVariable String commentId) {
        return ApiResponse.of(resources.get(projectId, nestedResource(parentResource, parentId), commentId));
    }

    @PutMapping("/{parentResource}/{parentId}/comments/{commentId}")
    ApiResponse<JsonNode> putNestedComment(@PathVariable UUID projectId,
                                           @PathVariable String parentResource,
                                           @PathVariable String parentId,
                                           @PathVariable String commentId,
                                           @RequestBody JsonNode body,
                                           @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.put(projectId, nestedResource(parentResource, parentId), commentId,
                body, actor(jwt)));
    }

    @PatchMapping("/{parentResource}/{parentId}/comments/{commentId}")
    ApiResponse<JsonNode> patchNestedComment(@PathVariable UUID projectId,
                                             @PathVariable String parentResource,
                                             @PathVariable String parentId,
                                             @PathVariable String commentId,
                                             @RequestBody JsonNode body,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(resources.patch(projectId, nestedResource(parentResource, parentId), commentId,
                body, actor(jwt)));
    }

    @DeleteMapping("/{parentResource}/{parentId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNestedComment(@PathVariable UUID projectId,
                             @PathVariable String parentResource,
                             @PathVariable String parentId,
                             @PathVariable String commentId, @AuthenticationPrincipal Jwt jwt) {
        resources.delete(projectId, nestedResource(parentResource, parentId), commentId, actor(jwt));
    }

    private String nestedResource(String parentResource, String parentId) {
        return "comments:" + parentResource + ":" + parentId;
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }
}
