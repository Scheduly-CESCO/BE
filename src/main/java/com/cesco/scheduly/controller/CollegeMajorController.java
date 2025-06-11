package com.cesco.scheduly.controller;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.Major;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/university")
public class CollegeMajorController {

    // 전체 단과대 리스트 반환
    @GetMapping("/colleges")
    public List<String> getColleges() {
        return Arrays.stream(College.values()).map(Enum::name).toList();
    }

    // 단과대학에 따른 전공 리스트 반환
    @GetMapping("/majors")
    public List<String> getMajorsByCollege(@RequestParam String collegeName) {
        try {
            College college = College.valueOf(collegeName.replace("&", "_"));
            return Major.getMajorsByCollege(college);
        } catch (IllegalArgumentException e) {
            return Collections.emptyList(); // 없는 단과대일 경우 빈 리스트 반환
        }
    }
}
