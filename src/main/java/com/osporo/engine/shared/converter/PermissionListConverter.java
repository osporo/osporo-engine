package com.osporo.engine.shared.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.osporo.engine.shared.enums.Permission;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PermissionListConverter
        implements AttributeConverter<List<Permission>, String[]> {

    @Override
    public String[] convertToDatabaseColumn(List<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new String[]{};
        }

        return permissions.stream()
                .map(Permission::name)
                .toArray(String[]::new);
    }

    @Override
    public List<Permission> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return new ArrayList<>();
        }

        return Arrays.stream(dbData)
                .map(Permission::valueOf)
                .collect(Collectors.toList());
    }
}
