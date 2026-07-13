package vn.uytinmang.projectos.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class ReadModelCacheInvalidationFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final ReadModelCache cache;

    ReadModelCacheInvalidationFilter(ReadModelCache cache) {
        this.cache = cache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (!MUTATIONS.contains(request.getMethod()) || response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }
        String[] segments = request.getRequestURI().split("/");
        if (segments.length > 4 && "projects".equals(segments[3])) {
            try {
                cache.invalidateProject(UUID.fromString(segments[4]));
            } catch (IllegalArgumentException ignored) {
                // Collection-level project creation has no existing read-model cache to invalidate.
            }
        }
        if (segments.length > 5 && "admin".equals(segments[3]) && "users".equals(segments[4])) {
            cache.invalidateSubject(segments[5]);
        }
    }
}
