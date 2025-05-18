package com.cesco.scheduly.dto.course;

import lombok.Data;
import java.util.List;

@Data
public class CourseListRequest {
    private List<String> courseCodes; // 학수번호 리스트
}