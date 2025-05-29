package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.PreferencesRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

  @PostMapping
  public ResponseEntity<?> savePreferences(@RequestBody PreferencesRequest dto) {
    // TODO:
    System.out.println("수강한 과목: " + dto.getCompleted_lectures());
    System.out.println("필수 과목: " + dto.getRequired_lectures());
    System.out.println("재수강 과목: " + dto.getRetake_lectures());

    return ResponseEntity.ok(Map.of("status", "saved"));
  }
}