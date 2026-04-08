package com.futsal.tournament.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 변환 실패", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String jsonData = dbData;

            // 이중 인코딩된 경우 처리 (예: "\"[\\\"url1\\\"]\"")
            // 문자열이 따옴표로 시작하면 먼저 String으로 파싱해서 언이스케이프
            if (jsonData.startsWith("\"") && jsonData.endsWith("\"")) {
                jsonData = objectMapper.readValue(jsonData, String.class);
            }

            return objectMapper.readValue(jsonData, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 파싱 실패: " + dbData, e);
        }
    }
}
