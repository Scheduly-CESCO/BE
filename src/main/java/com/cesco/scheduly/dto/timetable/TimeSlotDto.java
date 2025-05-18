package com.cesco.scheduly.dto.timetable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDto {
    @JsonProperty("요일")
    private String day;

    @JsonProperty("교시들")
    private List<Integer> periods;
}