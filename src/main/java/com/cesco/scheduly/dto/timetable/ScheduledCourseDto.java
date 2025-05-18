package com.cesco.scheduly.dto.timetable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledCourseDto {
    private String courseCode;    // 학수번호
    private String courseName;    // 교과목명
    private String department;    // 개설영역 (또는 일반화된 타입)
    private int credits;       // 학점
    private String professor;     // 담당교수
    private String classroom;     // 강의실
    private String remarks;       // 비고
    // 이 과목이 해당 시간표에서 실제로 배정된 시간 정보 (요일과 교시 리스트)
    // 한 과목이 여러 시간대에 걸쳐 수업하는 경우 (예: 주 2회) 모두 포함
    private List<TimeSlotDto> actualClassTimes;
}