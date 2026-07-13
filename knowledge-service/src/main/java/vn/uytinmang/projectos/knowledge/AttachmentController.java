package vn.uytinmang.projectos.knowledge;

import org.springframework.http.CacheControl;
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
import java.util.UUID;
import vn.uytinmang.projectos.platform.api.ApiResponse;

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
                                                                @RequestParam String storagePath) {
        return ApiResponse.of(storage.upload(projectId, storagePath, file));
    }

    @GetMapping("/content")
    ResponseEntity<byte[]> download(@PathVariable UUID projectId, @RequestParam String storagePath) {
        var object = storage.download(projectId, storagePath);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(object.contentType())).body(object.bytes());
    }

    @DeleteMapping("/content")
    ResponseEntity<Void> delete(@PathVariable UUID projectId, @RequestParam String storagePath) {
        storage.delete(projectId, storagePath);
        return ResponseEntity.noContent().build();
    }
}
