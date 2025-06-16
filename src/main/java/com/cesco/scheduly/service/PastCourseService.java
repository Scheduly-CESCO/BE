package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.PastCourseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PastCourseService {

    private List<PastCourseDto> pastCourses = new ArrayList<>();

    // 애플리케이션 시작 시 JSON 파일을 읽어 메모리에 저장
    @PostConstruct
    public void loadPastCourses() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // JSON 파일 경로를 정확히 지정합니다.
            ClassPathResource resource = new ClassPathResource("data/courses_past.json");
            this.pastCourses = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        } catch (Exception e) {
            // 실제 운영 시에는 로그를 남기는 것이 좋습니다.
            e.printStackTrace();
        }
    }

    // 메모리에 로드된 데이터를 기반으로 검색 수행
    public List<PastCourseDto> search(String query) {
        if (query == null || query.isBlank() || query.length() < 2) {
            return Collections.emptyList(); // 너무 짧은 검색어는 무시
        }

        String lowerCaseQuery = query.toLowerCase();

        return pastCourses.stream()
                .filter(course ->
                        (course.getCourseName() != null && course.getCourseName().toLowerCase().contains(lowerCaseQuery)) ||
                                (course.getCourseCode() != null && course.getCourseCode().toLowerCase().contains(lowerCaseQuery))
                )
                .limit(20) // 검색 결과가 너무 많지 않도록 최대 20개로 제한
                .collect(Collectors.toList());
    }
}