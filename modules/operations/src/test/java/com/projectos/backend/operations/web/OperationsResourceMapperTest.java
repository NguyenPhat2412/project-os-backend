package com.projectos.backend.operations.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationsResourceMapperTest {
    private final OperationsResourceMapper mapper = new OperationsResourceMapper();

    @Test
    void mapsLegacyContractDatesStoredAsDisplayStrings() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ctr-01");
        row.put("employeeUuid", "e0000000-0000-0000-0000-000000000001");
        row.put("effectiveDate", "01/08/2024");
        row.put("signDate", "01/08/2024");
        row.put("expireDate", null);

        OperationsResourceDto dto = mapper.toDto(row);

        assertThat((Object) dto.effectiveDate()).isEqualTo("01/08/2024");
        assertThat((Object) dto.signDate()).isEqualTo("01/08/2024");
        assertThat(dto.expireDate()).isNull();
    }
}
