package com.projectos.backend.platform.organization;

import java.util.List;
import java.util.UUID;

/** In-process port for organization-owned notification categories. */
public interface NotificationCategoryDirectory {
    List<Category> activeCategories(UUID organizationId);

    void requireActiveCategory(UUID organizationId, String code);

    record Category(UUID id, String code, String name, int displayOrder) {}
}
