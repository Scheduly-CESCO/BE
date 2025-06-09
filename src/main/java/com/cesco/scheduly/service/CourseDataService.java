package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CourseDataService {
    private static final Logger logger = LoggerFactory.getLogger(CourseDataService.class);
    private List<DetailedCourseInfo> allDetailedCourses = new ArrayList<>();
    private Map<String, CourseInfo> courseCatalogForSearch = new ConcurrentHashMap<>();

    private static final List<String> RESTRICTED_COURSE_KEYWORDS =
            List.of("군사학", "경상대학", "교직", "인문대학", "자연과학대학", "폴란드학과", "한국학과", "이공계열","우크라이나학과","그리스·불가리아학과",
                    "중앙아시아학과","루마니아학과", "AI융합대학", "공과대학(공과계열)", "CULTURE&TECHNOLOGY융합대학", "체코·슬로바키아학과", "아프리카학부");

    @PostConstruct
    public void loadAndProcessCourseData() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            ClassPathResource resource = new ClassPathResource("data/everytime_courses.json");

            List<DetailedCourseInfo> loadedCourses = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<DetailedCourseInfo>>() {}
            );

            this.allDetailedCourses = loadedCourses.stream()
                    .filter(c -> c != null && c.getCourseCode() != null && !c.getCourseCode().isBlank() && c.getCourseName() != null && !c.getCourseName().isBlank())
                    .map(course -> {
                        // DetailedCourseInfo 객체 필드 설정 (Lombok @Builder 또는 생성자 사용 권장)
                        DetailedCourseInfo processedCourse = new DetailedCourseInfo();
                        processedCourse.setCourseCode(course.getCourseCode());
                        processedCourse.setCourseName(course.getCourseName());
                        processedCourse.setDepartmentOriginal(course.getDepartmentOriginal());
                        processedCourse.setSpecificMajor(course.getSpecificMajor()); // "세부전공" 필드
                        processedCourse.setCredits(course.getCredits());
                        processedCourse.setTotalHours(course.getTotalHours()); // JSON에 "시간" 필드가 있다면

                        // groupId 설정
                        if (course.getCourseCode().length() >= 7) {
                            processedCourse.setGroupId(course.getCourseCode().substring(0, 7));
                        } else {
                            processedCourse.setGroupId(course.getCourseCode());
                        }

                        // 1차 일반화된 타입 설정
                        processedCourse.setGeneralizedType(determineInitialGeneralizedType(course.getDepartmentOriginal()));

                        // 추천 제한 과목 여부 설정
                        String generalizedType = processedCourse.getGeneralizedType();
                        processedCourse.setRestrictedCourse(
                                !(Objects.equals(generalizedType, "교양") || Objects.equals(generalizedType, "전공_후보"))
                        );
                        // 또는 RESTRICTED_COURSE_KEYWORDS 사용 로직 (현재는 위 규칙이 우선)
                        // String deptOriginalLower = course.getDepartmentOriginal() != null ? course.getDepartmentOriginal().toLowerCase() : "";
                        // if (RESTRICTED_COURSE_KEYWORDS.stream().anyMatch(keyword -> deptOriginalLower.contains(keyword.toLowerCase()))) {
                        //    processedCourse.setRestrictedCourse(true);
                        // }


                        // 나머지 필드 null 처리 등
                        if (course.getScheduleSlots() == null) {
                            processedCourse.setScheduleSlots(Collections.emptyList());
                        } else {
                            processedCourse.setScheduleSlots(course.getScheduleSlots());
                        }
                        String grade = course.getGrade();
                        if (grade == null || grade.trim().isEmpty() || grade.equalsIgnoreCase("null")) {
                            processedCourse.setGrade("정보없음");
                        } else {
                            processedCourse.setGrade(grade.trim());
                        }
                        processedCourse.setProfessor(course.getProfessor());
                        processedCourse.setClassroom(course.getClassroom());
                        processedCourse.setRemarks(course.getRemarks());

                        return processedCourse;
                    })
                    .collect(Collectors.toList());

            Map<String, CourseInfo> tempCatalog = new ConcurrentHashMap<>();
            this.allDetailedCourses.forEach(dc -> {
                CourseInfo ci = new CourseInfo(dc.getCourseCode(), dc.getCourseName(), dc.getDepartmentOriginal(), dc.getCredits(), dc.getGrade());
                tempCatalog.putIfAbsent(dc.getCourseCode(), ci);
            });
            this.courseCatalogForSearch.clear();
            this.courseCatalogForSearch.putAll(tempCatalog);

            logger.info("{}개의 상세 강의 정보 로드 및 처리 완료 (CourseDataService).", this.allDetailedCourses.size());

        } catch (IOException e) {
            logger.error("강의 데이터(everytime_courses.json) 로드/파싱 실패: {}", e.getMessage(), e);
            this.allDetailedCourses = new ArrayList<>();
            this.courseCatalogForSearch.clear();
        }
    }

    /**
     * "개설영역" 문자열을 기반으로 과목의 1차적인 일반화된 유형을 결정합니다.
     * 학생의 새로운 규칙: "교양", "전공"이 아닌 개설영역은 원본값을 generalizedType으로 사용하거나 특정 카테고리로 분류.
     */
    public String determineInitialGeneralizedType(String departmentOriginal) {
        if (departmentOriginal == null || departmentOriginal.trim().isEmpty()) {
            return "기타";
        }
        String deptLower = departmentOriginal.toLowerCase().trim();

        // 학생의 규칙: "개설영역"이 '교양' 또는 '전공'이 아닌 데이터는 모두 '특정 사용자 대상 과목'
        // 여기서 '특정 사용자 대상 과목'을 어떻게 generalizedType으로 표현할지 결정 필요
        // 1. "교양"으로 명확히 분류
        if (deptLower.equals("교양")) {
            return "교양";
        }

        // 2. "전공"으로 명확히 분류 (TimetableService에서 사용자 전공과 비교하여 최종 결정될 "후보")
        if (deptLower.equals("전공")) {
            return "전공_후보";
        }

        // 3. 그 외 (학생 규칙에 따라 '특정 사용자 대상 과목'으로 간주될 수 있는 것들)
        //    RESTRICTED_COURSE_KEYWORDS를 사용하여 명시적으로 분류
        for (String restrictedKeyword : RESTRICTED_COURSE_KEYWORDS) {
            if (deptLower.contains(restrictedKeyword.toLowerCase())) {
                // 각 키워드에 맞는 구체적인 타입 반환 (예: "군사학", "교직")
                if (restrictedKeyword.equalsIgnoreCase("군사학")) return "군사학";
                if (restrictedKeyword.equalsIgnoreCase("교직")) return "교직";
                if (restrictedKeyword.equalsIgnoreCase("인문대학")) return "인문대학";
                if (restrictedKeyword.equalsIgnoreCase("경상대학")) return "경상대학";
                if (restrictedKeyword.equalsIgnoreCase("자연과학대학")) return "자연과학대학";
                if (restrictedKeyword.equalsIgnoreCase("폴란드학과")) return "폴란드학과";
                if (restrictedKeyword.equalsIgnoreCase("한국학과")) return "한국학과";
                if (restrictedKeyword.equalsIgnoreCase("이공계열")) return "이공계열";
                if (restrictedKeyword.equalsIgnoreCase("우크라이나학과")) return "우크라이나학과";
                if (restrictedKeyword.equalsIgnoreCase("그리스·불가리아학과")) return "그리스·불가리아학과";
                if (restrictedKeyword.equalsIgnoreCase("중앙아시아학과")) return "중앙아시아학과";
                if (restrictedKeyword.equalsIgnoreCase("루마니아학과")) return "루마니아학과";
                if (restrictedKeyword.equalsIgnoreCase("AI융합대학")) return "AI융합대학";
                if (restrictedKeyword.equalsIgnoreCase("공과대학(공과계열)")) return "공과대학(공과계열)";
                if (restrictedKeyword.equalsIgnoreCase("CULTURE&TECHNOLOGY융합대학")) return "CULTURE&TECHNOLOGY융합대학";
                if (restrictedKeyword.equalsIgnoreCase("체코·슬로바키아학과")) return "체코·슬로바키아학과";
                if (restrictedKeyword.equalsIgnoreCase("아프리카학부")) return "아프리카학부";



                return departmentOriginal; // 명확한 매핑 없으면 원본값 또는 더 일반적인 "특수과목"
            }
        }

        // "학부", "학과" 등을 포함하지만 위에서 "전공"으로 명시되지 않은 경우 -> "전공_후보"
        // (TimetableService에서 "세부전공"과 비교하여 최종 결정)
        if (deptLower.contains("학부") || deptLower.contains("학과") || deptLower.contains("전공")) {
            // (이미 deptLower.equals("전공")은 위에서 "전공_후보"로 처리됨)
            // deptLower.contains("전공")은 "이중전공", "기타전공" 등도 포함할 수 있으므로 주의
            return "전공_후보";
        }

        // 위 규칙에 해당하지 않는 모든 것은 "기타" 또는 더 구체적인 "특수과목_미분류" 등으로 처리
        return "기타";
    }

    // (getDetailedCourses, getCourseInfoByCode, getDetailedCourseByCode, searchCourses 메소드는 이전 답변과 동일하게 유지)
    public List<DetailedCourseInfo> getDetailedCourses() {
        return Collections.unmodifiableList(allDetailedCourses);
    }

    public CourseInfo getCourseInfoByCode(String courseCode) {
        if (courseCode == null) return null;
        return courseCatalogForSearch.get(courseCode);
    }

    public DetailedCourseInfo getDetailedCourseByCode(String courseCode) {
        if (courseCode == null) return null;
        return allDetailedCourses.stream()
                .filter(c -> Objects.equals(courseCode, c.getCourseCode()))
                .findFirst().orElse(null);
    }

    //과목 검색
    public List<CourseInfo> searchCourses(String query, String department, String grade) {
        String lowerCaseQuery = (query != null && !query.isBlank()) ? query.toLowerCase().trim() : null;
        String deptFilter = (department != null && !department.isBlank()) ? department.trim() : null;
        String gradeFilter = (grade != null && !grade.isBlank()) ? grade.trim() : null;

        return courseCatalogForSearch.values().stream()
                .filter(course -> {
                    if (course == null) return false;

                    boolean nameMatch = lowerCaseQuery == null ||
                            (course.getCourseName() != null && course.getCourseName().toLowerCase().contains(lowerCaseQuery)) ||
                            (course.getCourseCode() != null && course.getCourseCode().toLowerCase().contains(lowerCaseQuery));

                    boolean deptMatch = deptFilter == null ||
                            (course.getDepartment() != null && course.getDepartment().equals(deptFilter));

                    boolean gradeMatch = true;
                    if (gradeFilter != null) {
                        String courseGrade = course.getGrade();
                        if (gradeFilter.equalsIgnoreCase("정보없음")) {
                            gradeMatch = (courseGrade == null || courseGrade.equals("정보없음") || courseGrade.trim().isEmpty());
                        } else if (courseGrade == null || courseGrade.equals("정보없음") || courseGrade.trim().isEmpty()) {
                            gradeMatch = false;
                        } else {
                            gradeMatch = courseGrade.contains(gradeFilter.replaceAll("[^0-9]", ""));
                        }
                    }
                    return nameMatch && deptMatch && gradeMatch;
                })
                .collect(Collectors.toList());
    }
}