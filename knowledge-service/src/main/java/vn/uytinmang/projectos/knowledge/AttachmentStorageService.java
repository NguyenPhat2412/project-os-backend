package vn.uytinmang.projectos.knowledge;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.uytinmang.projectos.platform.api.ApiException;

@Service
class AttachmentStorageService {
    static final long MAX_SIZE = 20L * 1024 * 1024;
    private final MinioClient minio;
    private final String bucket;

    AttachmentStorageService(MinioClient minio, @Value("${app.storage.bucket}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    AttachmentView upload(UUID projectId, String storagePath, MultipartFile file) {
        validate(projectId, storagePath, file);
        String safeName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectName = storagePath.replaceAll("/+$", "") + "/" + UUID.randomUUID() + "_" + safeName;
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        try (InputStream input = file.getInputStream()) {
            ensureBucket();
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName)
                    .contentType(contentType).stream(input, file.getSize(), -1L).build());
            String url = "/api/v1/projects/" + projectId + "/attachments/content?storagePath="
                    + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
            return new AttachmentView(file.getOriginalFilename(), url, objectName, file.getSize(), contentType,
                    LocalDate.now().toString());
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    StoredObject download(UUID projectId, String storagePath) {
        validateProjectPath(projectId, storagePath);
        try {
            var stat = minio.statObject(StatObjectArgs.builder().bucket(bucket).object(storagePath).build());
            try (InputStream input = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(storagePath).build())) {
                return new StoredObject(input.readAllBytes(), stat.contentType());
            }
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "attachment_not_found", "Attachment not found");
        }
    }

    void delete(UUID projectId, String storagePath) {
        validateProjectPath(projectId, storagePath);
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void validate(UUID projectId, String storagePath, MultipartFile file) {
        validateProjectPath(projectId, storagePath);
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "empty_file", "File is empty");
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Maximum file size is 20 MB");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_filename", "File name is required");
        }
    }

    private void validatePath(String storagePath) {
        if (storagePath == null || !storagePath.startsWith("projects/") || storagePath.contains("..")
                || storagePath.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_storage_path", "Invalid storage path");
        }
    }

    private void validateProjectPath(UUID projectId, String storagePath) {
        validatePath(storagePath);
        if (!storagePath.startsWith("projects/" + projectId + "/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_storage_path", "Storage path is outside the project");
        }
    }

    private ApiException storageFailure(Exception exception) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "storage_unavailable",
                "Object storage is unavailable: " + exception.getClass().getSimpleName());
    }

    record AttachmentView(String name, String url, String storagePath, long size, String contentType,
                          String uploadedAt) {
    }

    record StoredObject(byte[] bytes, String contentType) {
    }
}
