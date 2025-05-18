package com.cesco.scheduly.model;

import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 알려지지 않은 다른 필드는 계속 무시하도록 유지
public class DetailedCourseInfo {

    @JsonProperty("학수번호")
    private String courseCode;

    @JsonProperty("교과목명")
    private String courseName;

    @JsonProperty("개설영역")
    private String departmentOriginal;

    private String generalizedType; // 예: "전공", "교양" (서비스 로직에서 채움)

    @JsonProperty("학점")
    private int credits;

    @JsonProperty("시간") // JSON의 "시간" 필드 추가
    private int totalHours;   // 주당 총 수업 시간 (정수형으로 가정, 필요시 String으로 변경 후 파싱)

    @JsonProperty("학년")
    private String grade;

    @JsonProperty("담당교수")
    private String professor;

    @JsonProperty("강의실")
    private String classroom;

    @JsonProperty("비고")
    private String remarks;

    @JsonProperty("시간표정보")
    private List<TimeSlotDto> scheduleSlots;

    private boolean isRestrictedCourse; // 추천 제한 여부 (서비스 로직에서 채움)
}