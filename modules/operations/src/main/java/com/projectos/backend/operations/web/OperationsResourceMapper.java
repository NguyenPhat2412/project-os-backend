package com.projectos.backend.operations.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts the legacy JDBC adapter's internal row shape into the public DTO. */
@Component
public class OperationsResourceMapper {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public OperationsResourceMapper() {}

    public OperationsResourceDto toDto(Map<String, Object> row) {
        return objectMapper.convertValue(row, OperationsResourceDto.class);
    }
}
