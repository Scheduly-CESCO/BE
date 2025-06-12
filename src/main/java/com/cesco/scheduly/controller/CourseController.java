package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.CourseSearchResponse;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseDataService courseDataService;
    private final UserService userService; // UserService 추가

    @Autowired
    public CourseController(CourseDataService courseDataService, UserService userService) {
        this.courseDataService = courseDataService;
        this.userService = userService;
    }


    @GetMapping("/search")
    public ResponseEntity<CourseSearchResponse> searchCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String grade) {
        List<CourseInfo> courses = courseDataService.searchCourses(q, department, grade);
        return ResponseEntity.ok(new CourseSearchResponse(courses));
    }

    @GetMapping("/user-selections")
    public ResponseEntity<List<DetailedCourseInfo>> getUserSelectionCourses(@RequestParam Long userId) {
        List<String> courseCodes = userService.getRequiredAndRetakeCourses(userId);
        List<DetailedCourseInfo> courses = courseCodes.stream()
                .map(courseDataService::getDetailedCourseByCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return ResponseEntity.ok(courses);
    }
}