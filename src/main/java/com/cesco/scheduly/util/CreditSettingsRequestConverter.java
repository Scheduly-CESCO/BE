package com.cesco.scheduly.util;

import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import jakarta.persistence.Converter;

@Converter(autoApply = false) // UserPreferenceEntity에서 명시적으로 지정
public class CreditSettingsRequestConverter extends JsonAttributeConverter<CreditSettingsRequest> {
    public CreditSettingsRequestConverter() {
        super(CreditSettingsRequest.class);
    }
}