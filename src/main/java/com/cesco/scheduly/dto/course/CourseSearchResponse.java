package com.cesco.scheduly.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class CourseSearchResponse {
    private List<com.cesco.scheduly.dto.course.CourseInfo> courses;
}