package com.cesco.scheduly.model;

import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // JSON에 있지만 클래스에 없는 필드는 무시
public class DetailedCourseInfo {

    @JsonProperty("학수번호")
    private String courseCode;

    @JsonProperty("교과목명")
    private String courseName;

    @JsonProperty("개설영역")
    private String departmentOriginal; // 원본 "개설영역"

    @JsonProperty("세부전공") // 새로운 필드
    private String specificMajor;    // "세부전공" 컬럼 값

    private String groupId; // 학수번호 앞 7자리 (동일과목 판단용) - 서비스 로직에서 채움

    private String day;          // 강의 요일 (예: "Mon", "Tue")
    private int startPeriod;     // 시작 교시
    private int endPeriod;       // 종료 교시


    private String generalizedType; // 1차 분류된 과목 유형 (예: "교양", "전공_후보", "군사학") - 서비스 로직에서 채움

    @JsonProperty("학점")
    private int credits;

    @JsonProperty("시간")
    private int totalHours;

    @JsonProperty("학년")
    private String grade;

    @JsonProperty("담당교수")
    private String professor;

    @JsonProperty("강의실")
    private String classroom;

    @JsonProperty("비고")
    private String remarks;

    //JSON 파싱 시에만 임시로 사용
    @JsonProperty("시간표정보")
    private List<TimeSlotDto> scheduleSlots;

    private boolean isRestrictedCourse; // 추천 제한 여부 - 서비스 로직에서 채움


    // ★★★ 여러 시간대 수업을 처리하기 위한 추가 생성자 ★★★
    public DetailedCourseInfo(String courseCode, String courseName, String departmentOriginal, String specificMajor,
                              String groupId, String generalizedType, int credits, String grade,
                              String professor, String classroom, String remarks,
                              List<TimeSlotDto> scheduleSlots, boolean isRestrictedCourse) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.departmentOriginal = departmentOriginal;
        this.specificMajor = specificMajor;
        this.groupId = groupId;
        this.generalizedType = generalizedType;
        this.credits = credits;
        this.grade = grade;
        this.professor = professor;
        this.classroom = classroom;
        this.remarks = remarks;
        this.scheduleSlots = scheduleSlots;
        this.isRestrictedCourse = isRestrictedCourse;

        // scheduleSlots 정보를 바탕으로 day, startPeriod, endPeriod 계산
        if (scheduleSlots != null && !scheduleSlots.isEmpty()) {
            TimeSlotDto firstSlot = scheduleSlots.get(0); // 첫 번째 시간 정보 사용
            this.day = firstSlot.getDay();
            this.startPeriod = Collections.min(firstSlot.getPeriods());
            this.endPeriod = Collections.max(firstSlot.getPeriods());
        }
    }
}