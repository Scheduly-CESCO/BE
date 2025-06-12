package com.cesco.scheduly.dto.timetable;

import lombok.Data;
import java.util.List;

@Data
public class TimePreferenceRequest {

    // 예: [{'day': 'Mon', 'periods': [1,2,3]}, {'day':'Wed', 'periods': [4,5]}]
    private List<TimeSlotDto> preferredTimeSlots; // 특정 선호 시간대

}