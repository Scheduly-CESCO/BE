package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.CourseSearchResponse;
import com.cesco.scheduly.dto.course.PastCourseDto;
import com.cesco.scheduly.dto.course.PastCourseSearchResponse;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.PastCourseService;
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
    private final PastCourseService pastCourseService; // PastCourseService 추가

    @Autowired
    public CourseController(CourseDataService courseDataService, UserService userService,
                            PastCourseService pastCourseService) {
        this.courseDataService = courseDataService;
        this.userService = userService;
        this.pastCourseService = pastCourseService;

    }

    @GetMapping("/search")
    public ResponseEntity<CourseSearchResponse> searchCourses(
            @RequestParam("q") String query) { // 파라미터 이름을 'q'로 명시하고, 필수 값으로 변경합니다.
        // department와 grade 파라미터에 null을 전달하여 해당 필터링 조건을 무시하도록 합니다.
        List<CourseInfo> courses = courseDataService.searchCourses(query, null, null);
        return ResponseEntity.ok(new CourseSearchResponse(courses));
    }

    @GetMapping("/past-search")
    public ResponseEntity<PastCourseSearchResponse> searchPastCourses(@RequestParam("q") String query) {
        List<PastCourseDto> results = pastCourseService.search(query);
        return ResponseEntity.ok(new PastCourseSearchResponse(results));
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