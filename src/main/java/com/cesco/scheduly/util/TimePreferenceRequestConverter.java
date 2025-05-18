package com.cesco.scheduly.util;

import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import jakarta.persistence.Converter;

@Converter(autoApply = false) // UserPreferenceEntity에서 명시적으로 지정
public class TimePreferenceRequestConverter extends JsonAttributeConverter<TimePreferenceRequest> {
    public TimePreferenceRequestConverter() {
        super(TimePreferenceRequest.class);
    }
}