package com.cesco.scheduly.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedTimetableDto {
    private int timetableId; // 추천 번호 (1, 2, 3)
    private List<ScheduledCourseDto> scheduledCourses;
    private Map<String, Integer> creditsByType; // 유형별 실제 수강 학점 예: {"전공": 9, "교양": 6}
    private int totalCredits;
    // private double score; // (선택) 시간표 만족도 점수
}