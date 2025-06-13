package com.cesco.scheduly.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MainPageTimetableResponse {
    private boolean hasTimetable; // 시간표 존재 여부
    private RecommendedTimetableDto timetable; // 시간표 데이터 (없으면 null)
    private String message; // 안내 메시지
}