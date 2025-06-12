package com.cesco.scheduly.controller;

import com.cesco.scheduly.util.GraduationRequirementUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/graduation")
public class GraduationController {

    @GetMapping("/credits")
    public ResponseEntity<?> getRequiredCredits(
            @RequestParam String studentId,
            @RequestParam String college
    ) {
        int year = GraduationRequirementUtil.extractAdmissionYear(studentId);
        int requiredCredits = GraduationRequirementUtil.getGraduationCredits(college, year);
        return ResponseEntity.ok(Map.of(
                "studentId", studentId,
                "admissionYear", year,
                "college", college,
                "requiredCredits", requiredCredits
        ));
    }
}