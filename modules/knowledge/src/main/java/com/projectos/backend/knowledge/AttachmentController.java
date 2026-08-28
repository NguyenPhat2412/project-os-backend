package com.projectos.backend.knowledge;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;
import com.projectos.backend.platform.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/attachments")
class AttachmentController {
    private final AttachmentStorageService storage;

    AttachmentController(AttachmentStorageService storage) {
        this.storage = storage;
    }

    @PostMapping(value = "/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AttachmentStorageService.AttachmentView> upload(@PathVariable UUID projectId,
                                                                @RequestPart MultipartFile file,
                                                                @RequestParam String storagePath,
                                                                @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(storage.upload(projectId, storagePath, file, actor(jwt), root(jwt)));
    }

    @GetMapping("/content")
    ResponseEntity<byte[]> download(@PathVariable UUID projectId, @RequestParam String storagePath,
                                    @AuthenticationPrincipal Jwt jwt) {
        var object = storage.download(projectId, storagePath, actor(jwt), root(jwt));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(object.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(object.fileName()).build().toString())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header("X-Content-Type-Options", "nosniff")
                .body(object.bytes());
    }

    @DeleteMapping("/content")
    ResponseEntity<Void> delete(@PathVariable UUID projectId, @RequestParam String storagePath,
                                @AuthenticationPrincipal Jwt jwt) {
        storage.delete(projectId, storagePath, actor(jwt), root(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")); }
    private boolean root(Jwt jwt) { return "ROOT_ADMIN".equals(jwt.getClaimAsString("role")); }
}
