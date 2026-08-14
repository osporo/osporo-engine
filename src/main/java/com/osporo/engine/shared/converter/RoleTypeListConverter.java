package com.osporo.engine.shared.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.osporo.engine.shared.enums.RoleType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleTypeListConverter implements AttributeConverter<List<RoleType>, String[]> {

    @Override
    public String[] convertToDatabaseColumn(List<RoleType> roles) {
        if (roles == null || roles.isEmpty()) return new String[]{};
        return roles.stream()
            .map(RoleType::name)
            .toArray(String[]::new);
    }

    @Override
    public List<RoleType> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) return new ArrayList<>();
        return Arrays.stream(dbData)
            .map(RoleType::valueOf)
            .collect(Collectors.toList());
    }
}
