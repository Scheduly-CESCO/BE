package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.LecturePrefixRequest;
import com.cesco.scheduly.service.LectureDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lectures")
public class LectureFilterController {

    @Autowired
    private LectureDataService lectureDataService;

    @PostMapping("/exclude-related")
    public ResponseEntity<?> excludeRelated(@RequestBody LecturePrefixRequest req) {
        List<String> result = lectureDataService.findLecturesByPrefix(req.getExclude_prefixes());
        return ResponseEntity.ok(Map.of("excluded", result));
    }
}