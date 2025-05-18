// src/main/java/com/cesco/scheduly/util/JsonAttributeConverter.java
package com.cesco.scheduly.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.io.IOException;

public abstract class JsonAttributeConverter<T> implements AttributeConverter<T, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> clazz;

    public JsonAttributeConverter(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting attribute to JSON string", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON string to attribute", e);
        }
    }
}