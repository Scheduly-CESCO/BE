package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CourseDataService {
    private List<DetailedCourseInfo> allDetailedCourses = new ArrayList<>();
    final Map<String, CourseInfo> courseCatalogForSearch = new ConcurrentHashMap<>();

    private static final List<String> RESTRICTED_COURSE_DEPARTMENTS = List.of("군사학", "신입생세미나", "교직");

    @PostConstruct
    public void loadAndProcessCourseData() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 만약을 위해 FAIL_ON_UNKNOWN_PROPERTIES를 false로 설정 (클래스 레벨의 @JsonIgnoreProperties와 중복될 수 있으나, 더 확실하게)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            File file = ResourceUtils.getFile("classpath:data/everytime_courses.json");
            // DetailedCourseInfo 클래스에 @JsonProperty로 모든 필드가 잘 매핑되어 있다면 직접 변환
            List<DetailedCourseInfo> loadedCourses = objectMapper.readValue(file, new TypeReference<List<DetailedCourseInfo>>() {});

            allDetailedCourses = loadedCourses.stream()
                    .filter(c -> c.getCourseCode() != null && !c.getCourseCode().isBlank() && c.getCourseName() != null)
                    .peek(course -> {
                        course.setGeneralizedType(mapDepartmentToGeneralizedType(course.getDepartmentOriginal()));
                        course.setRestrictedCourse(RESTRICTED_COURSE_DEPARTMENTS.contains(course.getDepartmentOriginal()));
                        if (course.getScheduleSlots() == null) {
                            course.setScheduleSlots(Collections.emptyList());
                        }
                        // 학년 필드가 null일 경우 기본값 설정
                        if (course.getGrade() == null) {
                            course.setGrade("정보없음");
                        }
                        // "시간" 필드 (totalHours)는 DetailedCourseInfo에 int로 선언했고 @JsonProperty("시간")으로 매핑했으므로
                        // Jackson이 JSON의 숫자 값을 int로 자동 변환 시도합니다.
                        // 만약 JSON의 "시간" 값이 문자열이라면, DetailedCourseInfo의 totalHours를 String으로 변경 후 여기서 parseInt하거나,
                        // 커스텀 Deserializer를 사용해야 합니다. 현재는 JSON에 숫자가 있다고 가정합니다.
                    })
                    .collect(Collectors.toList());

            // 검색용 카탈로그 생성 (CourseInfo는 totalHours 필드가 없으므로, 필요시 추가)
            allDetailedCourses.forEach(dc -> {
                CourseInfo ci = new CourseInfo(dc.getCourseCode(), dc.getCourseName(), dc.getDepartmentOriginal(), dc.getCredits(), dc.getGrade());
                courseCatalogForSearch.putIfAbsent(dc.getCourseCode(), ci);
            });

            System.out.println(allDetailedCourses.size() + "개의 상세 강의 정보 로드 및 처리 완료 (CourseDataService).");

        } catch (IOException e) {
            System.err.println("강의 데이터(everytime_courses.json) 로드/파싱 실패: " + e.getMessage());
            e.printStackTrace();
            allDetailedCourses = new ArrayList<>();
        }
    }

    public String mapDepartmentToGeneralizedType(String departmentOriginal) {
        if (departmentOriginal == null) return "기타";
        if (departmentOriginal.contains("전공")) {
            if (departmentOriginal.contains("이중") || departmentOriginal.contains("부전공")) return "이중전공";
            return "전공";
        }
        if (departmentOriginal.contains("교양")) return "교양";
        if (RESTRICTED_COURSE_DEPARTMENTS.contains(departmentOriginal)) return departmentOriginal;
        return "기타";
    }

    public List<DetailedCourseInfo> getDetailedCourses() {
        return Collections.unmodifiableList(allDetailedCourses);
    }

    public CourseInfo getCourseInfoByCode(String courseCode) {
        return courseCatalogForSearch.get(courseCode);
    }

    public DetailedCourseInfo getDetailedCourseByCode(String courseCode) {
        return allDetailedCourses.stream()
                .filter(c -> c.getCourseCode().equals(courseCode))
                .findFirst().orElse(null);
    }

    public List<CourseInfo> searchCourses(String query, String department, String grade) {
        String lowerCaseQuery = (query != null && !query.isBlank()) ? query.toLowerCase().trim() : null;
        String deptFilter = (department != null && !department.isBlank()) ? department.trim() : null;
        String gradeFilter = (grade != null && !grade.isBlank()) ? grade.trim() : null;

        return courseCatalogForSearch.values().stream()
                .filter(course -> {
                    boolean nameMatch = lowerCaseQuery == null ||
                            (course.getCourseName() != null && course.getCourseName().toLowerCase().contains(lowerCaseQuery)) ||
                            (course.getCourseCode() != null && course.getCourseCode().toLowerCase().contains(lowerCaseQuery));
                    boolean deptMatch = deptFilter == null || (course.getDepartment() != null && course.getDepartment().equals(deptFilter));
                    boolean gradeMatch = gradeFilter == null || (course.getGrade() != null && (course.getGrade().contains(gradeFilter) || "전학년".equals(course.getGrade()) || course.getGrade().contains(gradeFilter.replaceAll("[^0-9]", ""))));
                    return nameMatch && deptMatch && gradeMatch;
                })
                .collect(Collectors.toList());
    }
}