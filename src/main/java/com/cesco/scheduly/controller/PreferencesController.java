package com.cesco.scheduly.controller;

import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
public class PreferencesController {

  private final UserService userService;
  private final CourseDataService courseDataService;

  // 기수강 과목 추가
  @PostMapping("/completed")
  public ResponseEntity<?> addCompletedLectures(
          @RequestParam String userId,
          @RequestBody List<String> completedLectures
  ) {
    userService.updateTakenCourses(userId, completedLectures);
    return ResponseEntity.ok(Map.of("status", "completed_saved"));
  }

  // 기수강 과목 삭제
  @DeleteMapping("/completed")
  public ResponseEntity<?> removeCompletedLectures(
          @RequestParam Long userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeTakenCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "completed_removed"));
  }

  // ✅ 필수 과목 추가
  @PostMapping("/required")
  public ResponseEntity<?> addRequiredLectures(
          @RequestParam String userId,
          @RequestBody List<String> requiredLectures
  ) {
    userService.updateMandatoryCourses(userId, requiredLectures);
    return ResponseEntity.ok(Map.of("status", "required_saved"));
  }

  // ✅ 필수 과목 삭제
  @DeleteMapping("/required")
  public ResponseEntity<?> removeRequiredLectures(
          @RequestParam Long userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeMandatoryCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "required_removed"));
  }

  // 재수강 과목 추가
  @PostMapping("/retake")
  public ResponseEntity<?> addRetakeLectures(
          @RequestParam String userId,
          @RequestBody List<String> retakeLectures
  ) {
    userService.updateRetakeCourses(userId, retakeLectures);
    return ResponseEntity.ok(Map.of("status", "retake_saved"));
  }

  // 재수강 과목 삭제
  @DeleteMapping("/retake")
  public ResponseEntity<?> removeRetakeLectures(
          @RequestParam Long userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeRetakeCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "retake_removed"));
  }

  @GetMapping("/retake/available")   // 재수강 과목에서 기수강 과목 조회
  public ResponseEntity<List<DetailedCourseInfo>> getRetakeCandidates(@RequestParam Long userId) {
    List<DetailedCourseInfo> taken = userService.getTakenCoursesWithDetails(userId);
    return ResponseEntity.ok(taken);
  }

  @GetMapping("/courses")
  public ResponseEntity<Map<String, List<DetailedCourseInfo>>> getUserCoursesByFilter(
          @RequestParam Long userId,
          @RequestParam(required = false) String courseCode // 필터링 파라미터 추가
  ) {
    UserCourseSelectionEntity selection = userService.getUserCourseSelectionByUserId(userId);

    Function<List<String>, List<DetailedCourseInfo>> toDetails = courseCodes -> courseCodes.stream()
            .filter(code -> courseCode == null || code.equals(courseCode)) // 필터링 조건
            .map(courseDataService::getDetailedCourseByCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    Map<String, List<DetailedCourseInfo>> result = Map.of(
            "taken", toDetails.apply(selection.getTakenCourses()),
            "mandatory", toDetails.apply(selection.getMandatoryCourses()),
            "retake", toDetails.apply(selection.getRetakeCourses())
    );
    return ResponseEntity.ok(result);
  }

  @GetMapping("/retake")
  public ResponseEntity<List<DetailedCourseInfo>> getRetakeCourses(@RequestParam Long userId) {
    List<String> retakeCodes = userService.getRetakeCourses(userId);
    List<DetailedCourseInfo> details = retakeCodes.stream()
            .map(courseCode -> courseDataService.getDetailedCourseByCode(courseCode))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    return ResponseEntity.ok(details);
  }
}