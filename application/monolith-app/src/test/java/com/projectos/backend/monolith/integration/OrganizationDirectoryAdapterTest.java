package com.projectos.backend.monolith.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projectos.backend.platform.organization.OrganizationDirectory;

class OrganizationDirectoryAdapterTest {

    @Test
    void adapterExposesOnlyTheSharedOrganizationDirectoryPort() {
        assertThat(OrganizationDirectoryAdapter.class.getInterfaces())
                .containsExactly(OrganizationDirectory.class);
    }
}
