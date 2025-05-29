package com.cesco.scheduly.controller;

import com.cesco.scheduly.service.Userservice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferencesController {

  private final Userservice userService;

  // 기수강 과목
  @PostMapping
  public ResponseEntity<?> savePreferencesLectures(
          @RequestParam String userId,
          @RequestBody List<String> completedLectures
  ) {
    userService.updateTakenCourses(userId, completedLectures);
    return ResponseEntity.ok(Map.of("status", "completed_saved"));
  }

  // 필수 과목
  @PostMapping("/required")
  public ResponseEntity<?> saveRequiredPreferences(
          @RequestParam String userId,
          @RequestBody List<String> requiredLectures
  ) {
    userService.updateMandatoryCourses(userId, requiredLectures);
    return ResponseEntity.ok(Map.of("status", "required_saved"));
  }

  // 재수강 과목
  @PostMapping("/retake")
  public ResponseEntity<?> saveRetakePreferences(
          @RequestParam String userId,
          @RequestBody List<String> retakeLectures
  ) {
    userService.updateTakenCourses(userId, retakeLectures);
    return ResponseEntity.ok(Map.of("status", "retake_saved"));
  }
}