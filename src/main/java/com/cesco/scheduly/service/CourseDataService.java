// src/main/java/com/cesco/scheduly/service/CourseDataService.java
package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.cesco.scheduly.entity.CourseEntity;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseDataService {

    private final CourseRepository courseRepository;
    private static final List<String> RESTRICTED_COURSE_KEYWORDS =
            List.of("군사학", "경상대학", "교직", "인문대학", "자연과학대학", "폴란드학과", "한국학과", "이공계열", "우크라이나학과", "그리스·불가리아학과",
                    "중앙아시아학과", "루마니아학과", "AI융합대학", "공과대학(공과계열)", "CULTURE&TECHNOLOGY융합대학", "체코·슬로바키아학과", "아프리카학부");


    public CourseDataService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    // TimetableService가 사용하는 메소드. DB에서 조회하여 DetailedCourseInfo 모델로 변환해준다.
    public List<DetailedCourseInfo> getDetailedCourses() {
        return courseRepository.findAll().stream()
                .map(this::entityToDetailedInfo)
                .collect(Collectors.toList());
    }

    public DetailedCourseInfo getDetailedCourseByCode(String courseCode) {
        return courseRepository.findById(courseCode)
                .map(this::entityToDetailedInfo)
                .orElse(null);
    }

    /**
     * 학수번호로 단일 강의의 상세 정보를 조회합니다.
     * @param courseCode 조회할 강의의 학수번호
     * @return 조회된 강의 정보 DTO (없으면 null 반환)
     */
    public CourseInfo getCourseByCode(String courseCode) {
        return courseRepository.findByCourseCode(courseCode) // 1. Repository 메소드 호출
                .map(entity -> new CourseInfo( // 2. 결과가 있으면 CourseInfo DTO로 변환
                        entity.getCourseCode(),
                        entity.getCourseName(),
                        entity.getDepartmentOriginal(),
                        entity.getCredits(),
                        entity.getGrade()))
                .orElse(null); // 3. 결과가 없으면 null 반환
    }

    // CourseController가 사용하는 메소드
    public List<CourseInfo> searchCourses(String query, String department, String grade) {
        return courseRepository.searchCourses(query, department, grade).stream()
                .map(entity -> new CourseInfo(entity.getCourseCode(), entity.getCourseName(), entity.getDepartmentOriginal(), entity.getCredits(), entity.getGrade()))
                .collect(Collectors.toList());
    }

    // LectureFilterController가 사용할 메소드 (기존 LectureDataService의 기능)
    public List<String> findCoursesByPrefix(List<String> prefixes) {
        return prefixes.stream()
                .flatMap(prefix -> courseRepository.findCourseCodesStartingWith(prefix).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    // DataInitializer에서 재사용하기 위해 public으로 유지
    public String determineInitialGeneralizedType(String departmentOriginal) {
        if (departmentOriginal == null || departmentOriginal.trim().isEmpty()) return "기타";
        String deptLower = departmentOriginal.toLowerCase().trim();
        if (deptLower.equals("교양")) return "교양";
        if (deptLower.equals("전공")) return "전공_후보";
        for (String restrictedKeyword : RESTRICTED_COURSE_KEYWORDS) {
            if (deptLower.contains(restrictedKeyword.toLowerCase())) return restrictedKeyword;
        }
        if (deptLower.contains("학부") || deptLower.contains("학과") || deptLower.contains("전공")) return "전공_후보";
        return "기타";
    }

    // DB Entity를 기존 서비스 로직이 사용하는 모델(DetailedCourseInfo)로 변환하는 어댑터 메소드
    private DetailedCourseInfo entityToDetailedInfo(CourseEntity entity) {
        if (entity == null) {
            return null;
        }

        // ★★★ TimeSlotEntity를 TimeSlotDto로 변환하는 로직 수정 ★★★
        // 1. DB에서 가져온 TimeSlotEntity들을 요일별로 그룹핑합니다.
        Map<String, List<Integer>> slotsByDay = entity.getScheduleSlots().stream()
                .collect(Collectors.groupingBy(
                        slot -> slot.getDay(), // "Mon", "Tue" 등으로 그룹
                        Collectors.mapping(slot -> slot.getPeriod(), Collectors.toList()) // 각 그룹의 교시들을 리스트로 만듦
                ));

        // 2. 그룹핑된 데이터를 우리가 원하는 최종 DTO 형태로 변환합니다.
        List<TimeSlotDto> finalScheduleSlots = slotsByDay.entrySet().stream()
                .map(entry -> new TimeSlotDto(entry.getKey(), entry.getValue())) // 수정된 생성자 사용
                .collect(Collectors.toList());

        // 3. 최종 변환된 시간 정보(finalScheduleSlots)를 DetailedCourseInfo에 담아 반환합니다.
        return new DetailedCourseInfo(
                entity.getCourseCode(),
                entity.getCourseName(),
                entity.getDepartmentOriginal(),
                entity.getSpecificMajor(),
                entity.getGroupId(),
                entity.getGeneralizedType(),
                entity.getCredits(),
                entity.getTotalHours(),
                entity.getGrade(),
                entity.getProfessor(),
                entity.getClassroom(),
                entity.getRemarks(),
                finalScheduleSlots, // 수정된 scheduleSlots 리스트를 전달
                entity.isRestrictedCourse()
        );
    }

    public Map<String, List<DetailedCourseInfo>> getUserCourseDetails(UserCourseSelectionEntity selection) {
        Function<List<String>, List<DetailedCourseInfo>> toDetails = courseCodes -> courseCodes.stream()
                .map(this::getDetailedCourseByCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Map.of(
                "taken", toDetails.apply(selection.getTakenCourses()),
                "mandatory", toDetails.apply(selection.getMandatoryCourses()),
                "retake", toDetails.apply(selection.getRetakeCourses())
        );
    }
}