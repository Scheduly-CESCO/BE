package com.cesco.scheduly.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfo {
    private String courseCode; // 학수번호
    private String courseName; // 교과목명
    private String department; // 개설영역
    private int credits;    // 학점
    private String grade;      // 학년 (String으로 처리하여 "1", "2", "전학년" 등 유연하게)
    // 시간표 정보 등 추가 필드는 추천 로직(5-9단계)에서 더 자세히 다뤄집니다.
}