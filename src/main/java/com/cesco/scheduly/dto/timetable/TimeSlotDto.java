package com.cesco.scheduly.dto.timetable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDto {
    @JsonProperty("요일")
    private String day;

    @JsonProperty("교시들")
    private List<Integer> periods;

    // ★★★ 프론트엔드 전달용 필드 추가 ★★★
    @JsonProperty("start_period")
    private int startPeriod;

    @JsonProperty("end_period")
    private int endPeriod;

    // List<Integer>를 받아 자동으로 min, max 값을 계산하는 생성자 추가
    public TimeSlotDto(String day, List<Integer> periods) {
        this.day = day;
        this.periods = periods;
        if (periods != null && !periods.isEmpty()) {
            this.startPeriod = Collections.min(periods);
            this.endPeriod = Collections.max(periods);
        }
    }
}