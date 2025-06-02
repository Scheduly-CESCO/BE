package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.CourseSearchResponse;
import com.cesco.scheduly.service.CourseDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseDataService courseDataService;

    @Autowired
    public CourseController(CourseDataService courseDataService) {
        this.courseDataService = courseDataService;
    }

    @GetMapping("/search")
    public ResponseEntity<CourseSearchResponse> searchCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String grade) {
        List<CourseInfo> courses = courseDataService.searchCourses(q, department, grade);
        return ResponseEntity.ok(new CourseSearchResponse(courses));
    }
}