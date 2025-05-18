package com.cesco.scheduly.dto.timetable;

import lombok.Data;
import java.util.List;

@Data
public class TimePreferenceRequest {
    // 예: ["Mon", "Wed"] 선호 요일
    private List<String> preferredDays;
    // 예: ["Fri"] 기피 요일
    private List<String> avoidDays;
    // 예: [{'day': 'Mon', 'periods': [1,2,3]}, {'day':'Wed', 'periods': [4,5]}]
    private List<TimeSlotDto> preferredTimeSlots; // 특정 선호 시간대
    private List<TimeSlotDto> avoidTimeSlots;     // 특정 기피 시간대
    // 예: true이면 공강일 최대한 확보, false이면 상관 없음
    private Boolean preferNoClassDays;
    // 예: [1, 2, 3, 4] 아침 수업 선호, [7, 8, 9] 저녁 수업 선호 등
    private List<Integer> preferredPeriodBlocks;
}