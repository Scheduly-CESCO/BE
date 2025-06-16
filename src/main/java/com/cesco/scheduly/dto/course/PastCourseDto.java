package com.cesco.scheduly.dto.course;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PastCourseDto {

    @JsonProperty("courseCode")
    private String courseCode;

    @JsonProperty("department")
    private String department;

    @JsonProperty("courseName")
    private String courseName;

    @JsonProperty("professor")
    private String professor;
}