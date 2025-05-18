package com.cesco.scheduly.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedTimetableResponse {
    private List<RecommendedTimetableDto> timetables;
    private String message; // 추가 메시지 (예: "3개의 시간표를 추천합니다.")
}