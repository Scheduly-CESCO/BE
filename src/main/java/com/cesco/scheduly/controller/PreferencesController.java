package com.cesco.scheduly.controller;

import com.cesco.scheduly.service.Userservice;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferencesController {

  private final Userservice userService;

  // 수강한 과목 저장
  @PostMapping("/completed")
  public ResponseEntity<?> saveCompletedLectures(
          @RequestParam String userId,
          @RequestBody List<String> completedLectures
  ) {
    userService.updateTakenCourses(userId, completedLectures);
    return ResponseEntity.ok(Map.of("status", "completed_saved"));
  }

  // 수강한 과목 삭제
  @DeleteMapping("/completed")
  public ResponseEntity<?> deleteCompletedLectures(
          @RequestParam String userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeTakenCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "completed_removed"));
  }

  // 필수 과목 저장
  @PostMapping("/required")
  public ResponseEntity<?> saveRequiredLectures(
          @RequestParam String userId,
          @RequestBody List<String> requiredLectures
  ) {
    userService.updateMandatoryCourses(userId, requiredLectures);
    return ResponseEntity.ok(Map.of("status", "required_saved"));
  }

  // 필수 과목 삭제
  @DeleteMapping("/required")
  public ResponseEntity<?> removeRequiredLectures(
          @RequestParam String userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeMandatoryCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "required_removed"));
  }

  // 재수강 과목 저장
  @PostMapping("/retake")
  public ResponseEntity<?> saveRetakeLectures(
          @RequestParam String userId,
          @RequestBody List<String> retakeLectures
  ) {
    userService.updateRetakeCourses(userId, retakeLectures);
    return ResponseEntity.ok(Map.of("status", "retake_saved"));
  }

  // 재수강 과목 삭제
  @DeleteMapping("/retake")
  public ResponseEntity<?> removeRetakeLectures(
          @RequestParam String userId,
          @RequestBody List<String> lecturesToRemove
  ) {
    userService.removeRetakeCourses(userId, lecturesToRemove);
    return ResponseEntity.ok(Map.of("status", "retake_removed"));
  }
}