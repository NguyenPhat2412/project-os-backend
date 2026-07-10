package vn.uytinmang.projectos.resource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.uytinmang.projectos.platform.api.ApiException;

@Component
class ResourceCatalog {
    private final Set<String> resources;
    private final Set<String> immutableResources;

    ResourceCatalog(@Value("${app.resources}") String resources,
                    @Value("${app.immutable-resources:}") String immutableResources) {
        this.resources = split(resources);
        this.immutableResources = split(immutableResources);
    }

    String require(String resource) {
        String baseResource = resource.contains(":") ? resource.substring(0, resource.indexOf(':')) : resource;
        if (!resources.contains(baseResource)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", "Unknown resource");
        }
        return resource;
    }

    void requireMutable(String resource) {
        require(resource);
        String baseResource = resource.contains(":") ? resource.substring(0, resource.indexOf(':')) : resource;
        if (immutableResources.contains(baseResource)) {
            throw new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "resource_immutable",
                    "This resource is append-only");
        }
    }

    private Set<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
