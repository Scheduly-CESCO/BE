package com.cesco.scheduly.dto.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PastCourseDto {

    @JsonProperty("학수번호")
    private String courseCode;

    @JsonProperty("개설영역")
    private String department;

    @JsonProperty("교과목명")
    private String courseName;

    @JsonProperty("담당교수")
    private String professor;
}