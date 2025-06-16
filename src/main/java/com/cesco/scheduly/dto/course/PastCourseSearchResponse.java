package com.cesco.scheduly.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class PastCourseSearchResponse {
    private List<PastCourseDto> courses;
}