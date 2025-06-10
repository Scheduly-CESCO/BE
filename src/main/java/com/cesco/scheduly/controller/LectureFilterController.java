package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.LecturePrefixRequest;
import com.cesco.scheduly.service.CourseDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lectures")
public class LectureFilterController {

    private final CourseDataService courseDataService; // 주입 대상 변경

    // 생성자 주입 방식으로 변경
    public LectureFilterController(CourseDataService courseDataService) {
        this.courseDataService = courseDataService;
    }
    @PostMapping("/exclude-related")
    public ResponseEntity<?> excludeRelated(@RequestBody LecturePrefixRequest req) {
        List<String> result = courseDataService.findCoursesByPrefix(req.getExclude_prefixes());
        return ResponseEntity.ok(Map.of("excluded", result));
    }
}