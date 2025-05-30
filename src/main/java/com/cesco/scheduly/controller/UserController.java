package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.ApiResponse;
import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.CourseSearchResponse;
import com.cesco.scheduly.dto.course.PreferencesRequest;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.dto.user.SignupResponse;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.exception.InvalidInputException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.Userservice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
public class UserController {

    private final Userservice userService;
    private final CourseDataService courseDataService; // getTakenCourses에서 사용

    @Autowired
    public UserController(Userservice userService, CourseDataService courseDataService) {
        this.userService = userService;
        this.courseDataService = courseDataService;
    }

    // 1. 사용자 등록 API
    @PostMapping("/users/register")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signupRequest) {
        try {
            User newUser = userService.registerUser(signupRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SignupResponse(newUser.getUserId(), "사용자 등록에 성공했습니다."));
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse(e.getMessage()));
        } catch (RuntimeException e) {
            System.err.println("사용자 등록 중 서버 오류: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("서버 내부 오류 발생: " + e.getMessage()));
        }
    }

    // 2. 사용자가 이전에 수강한 과목 업데이트 API
    @PutMapping("/users/{userId}/history/taken-courses")
    public ResponseEntity<ApiResponse> updateTakenCourses(
            @PathVariable String userId,
            @RequestBody PreferencesRequest preferencesRequest) {
        try {
            userService.updateTakenCourses(userId, preferenceRequest.getCourseCodes());
            return ResponseEntity.ok(new ApiResponse("수강 이력 업데이트 성공"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            System.err.println("수강 이력 업데이트 중 오류 (User ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("수강 이력 업데이트 중 서버 오류 발생"));
        }
    }

    // 사용자가 이전에 수강한 과목 조회 API (4단계 재수강 선택 시 프론트엔드에서 사용)
    @GetMapping("/users/{userId}/history/taken-courses")
    public ResponseEntity<?> getTakenCourses(@PathVariable String userId) {
        try {
            List<String> courseCodes = userService.getTakenCourses(userId);
            if (courseCodes == null) {
                courseCodes = Collections.emptyList();
            }
            List<CourseInfo> courses = courseCodes.stream()
                    .map(courseDataService::getCourseInfoByCode) // CourseDataService의 메소드 사용
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new CourseSearchResponse(courses));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            System.err.println("수강 이력 조회 중 오류 (User ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("수강 이력 조회 중 서버 오류 발생"));
        }
    }

    // 3. 이번 학기 필수 과목 업데이트 API
    @PutMapping("/users/{userId}/semester/mandatory-courses")
    public ResponseEntity<ApiResponse> updateMandatoryCourses(
            @PathVariable String userId,
            @RequestBody PreferencesRequest preferenceRequest) {
        try {
            userService.updateMandatoryCourses(userId, preferenceRequest.getCourseCodes());
            return ResponseEntity.ok(new ApiResponse("필수 과목 업데이트 성공"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            System.err.println("필수 과목 업데이트 중 오류 (User ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("필수 과목 업데이트 중 서버 오류 발생"));
        }
    }

    // 4. 이번 학기 재수강 과목 업데이트 API
    @PutMapping("/users/{userId}/semester/retake-courses")
    public ResponseEntity<ApiResponse> updateRetakeCourses(
            @PathVariable String userId,
            @RequestBody PreferencesRequest preferenceRequest) {
        try {
            userService.updateRetakeCourses(userId, preferenceRequest.getCourseCodes());
            return ResponseEntity.ok(new ApiResponse("재수강 과목 업데이트 성공"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            System.err.println("재수강 과목 업데이트 중 오류 (User ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("재수강 과목 업데이트 중 서버 오류 발생"));
        }
    }
}
