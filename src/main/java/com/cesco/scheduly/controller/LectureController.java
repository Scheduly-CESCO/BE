package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.LectureDto;
import com.cesco.scheduly.service.LectureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;

    @GetMapping
    public ResponseEntity<List<LectureDto>> getAllLectures() {
        return ResponseEntity.ok(lectureService.getAllLectures());
    }
}