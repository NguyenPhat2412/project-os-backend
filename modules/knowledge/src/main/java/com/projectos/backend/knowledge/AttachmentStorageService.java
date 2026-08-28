package com.projectos.backend.knowledge;

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
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.projectos.backend.platform.api.ApiException;
import com.projectos.backend.platform.project.ProjectPermissionChecker;

@Service
class AttachmentStorageService {
    static final long MAX_SIZE = 20L * 1024 * 1024;
    private final MinioClient minio;
    private final String bucket;
    private final ObjectProvider<ProjectPermissionChecker> permissions;

    AttachmentStorageService(MinioClient minio, @Value("${app.storage.bucket:project-os}") String bucket,
                              ObjectProvider<ProjectPermissionChecker> permissions) {
        this.minio = minio;
        this.bucket = bucket;
        this.permissions = permissions;
    }

    AttachmentView upload(UUID projectId, String storagePath, MultipartFile file, UUID actorId, boolean rootAdmin) {
        requireProjectPermission(projectId, "attachments", "create", actorId, rootAdmin);
        validate(projectId, storagePath, file);
        FileType fileType = detectFileType(file);
        String safeName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectName = storagePath.replaceAll("/+$", "") + "/" + UUID.randomUUID() + "_" + safeName;
        String contentType = fileType.contentType();
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

    StoredObject download(UUID projectId, String storagePath, UUID actorId, boolean rootAdmin) {
        requireProjectPermission(projectId, "attachments", "read", actorId, rootAdmin);
        validateProjectPath(projectId, storagePath);
        try {
            var stat = minio.statObject(StatObjectArgs.builder().bucket(bucket).object(storagePath).build());
            if (stat.size() > MAX_SIZE) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Maximum file size is 20 MB");
            }
            try (InputStream input = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(storagePath).build())) {
                byte[] bytes = input.readNBytes((int) MAX_SIZE + 1);
                if (bytes.length > MAX_SIZE) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Maximum file size is 20 MB");
                }
                return new StoredObject(bytes, stat.contentType(), downloadFileName(storagePath));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "attachment_not_found", "Attachment not found");
        }
    }

    void delete(UUID projectId, String storagePath, UUID actorId, boolean rootAdmin) {
        requireProjectPermission(projectId, "attachments", "delete", actorId, rootAdmin);
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

    private FileType detectFileType(MultipartFile file) {
        String name = file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        FileType type = FileType.BY_EXTENSION.get(extension);
        if (type == null) throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_file_type", "File type is not allowed");
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (!type.matches(header)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_file_type", "File content does not match its extension");
            }
            return type;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_file", "File could not be inspected");
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

    private void requireProjectPermission(UUID projectId, String resource, String action,
                                          UUID actorId, boolean rootAdmin) {
        if (rootAdmin) return;
        ProjectPermissionChecker checker = permissions.getIfAvailable();
        if (actorId == null || checker == null || !checker.allowed(projectId, actorId, resource, action)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "project_access_denied", "Project access denied");
        }
    }

    private String downloadFileName(String storagePath) {
        String name = storagePath.substring(storagePath.lastIndexOf('/') + 1);
        int separator = name.indexOf('_');
        String original = separator > 0 ? name.substring(separator + 1) : name;
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    record AttachmentView(String name, String url, String storagePath, long size, String contentType,
                          String uploadedAt) {
    }

    record StoredObject(byte[] bytes, String contentType, String fileName) {
    }

    private record FileType(String contentType, java.util.function.Predicate<byte[]> signature) {
        private static final Map<String, FileType> BY_EXTENSION = Map.ofEntries(
                Map.entry("pdf", new FileType("application/pdf", bytes -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII)))),
                Map.entry("png", new FileType("image/png", bytes -> startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G'}))),
                Map.entry("jpg", new FileType("image/jpeg", bytes -> startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))),
                Map.entry("jpeg", new FileType("image/jpeg", bytes -> startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))),
                Map.entry("gif", new FileType("image/gif", bytes -> startsWith(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII)))),
                Map.entry("doc", new FileType("application/msword", bytes -> startsWith(bytes, new byte[] {(byte) 0xd0, (byte) 0xcf, (byte) 0x11, (byte) 0xe0}))),
                Map.entry("xls", new FileType("application/vnd.ms-excel", bytes -> startsWith(bytes, new byte[] {(byte) 0xd0, (byte) 0xcf, (byte) 0x11, (byte) 0xe0}))),
                Map.entry("ppt", new FileType("application/vnd.ms-powerpoint", bytes -> startsWith(bytes, new byte[] {(byte) 0xd0, (byte) 0xcf, (byte) 0x11, (byte) 0xe0}))),
                Map.entry("docx", new FileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileType::zip)),
                Map.entry("xlsx", new FileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FileType::zip)),
                Map.entry("pptx", new FileType("application/vnd.openxmlformats-officedocument.presentationml.presentation", FileType::zip)),
                Map.entry("zip", new FileType("application/zip", FileType::zip)),
                Map.entry("txt", new FileType("text/plain", FileType::text)),
                Map.entry("csv", new FileType("text/csv", FileType::text)));

        private boolean matches(byte[] header) { return signature.test(header); }
        private static boolean zip(byte[] bytes) { return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K'; }
        private static boolean text(byte[] bytes) {
            for (byte value : bytes) if (value == 0) return false;
            return true;
        }
        private static boolean startsWith(byte[] value, byte[] prefix) {
            if (value.length < prefix.length) return false;
            for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
            return true;
        }
    }
}
