package com.cesco.scheduly.dto.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PastCourseDto {

    // JSON의 "학수번호" 키를 courseCode 필드에 매핑
    @JsonProperty("학수번호")
    private String courseCode;

    // JSON의 "개설영역" 키를 department 필드에 매핑
    @JsonProperty("개설영역")
    private String department;

    // JSON의 "교과목명" 키를 courseName 필드에 매핑
    @JsonProperty("교과목명")
    private String courseName;

    // JSON의 "담당교수" 키를 professor 필드에 매핑
    @JsonProperty("담당교수")
    private String professor;
}