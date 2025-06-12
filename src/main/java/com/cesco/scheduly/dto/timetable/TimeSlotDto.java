package com.cesco.scheduly.dto.timetable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class TimeSlotDto {

    @JsonProperty("요일")
    private String day;

    @JsonProperty("교시들")
    private List<Integer> periods;

    // 프론트엔드 전달용 계산 필드
    private int startPeriod;
    private int endPeriod;

    // JSON 파싱 시 Jackson이 사용할 생성자
    public TimeSlotDto(@JsonProperty("요일") String day, @JsonProperty("교시들") List<Integer> periods) {
        this.day = day;
        this.periods = periods;
        if (periods != null && !periods.isEmpty()) {
            this.startPeriod = Collections.min(periods);
            this.endPeriod = Collections.max(periods);
        }
    }
}