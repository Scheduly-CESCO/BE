package com.cesco.scheduly.service;

import com.cesco.scheduly.entity.CourseEntity;
import com.cesco.scheduly.entity.TimeSlotEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.repository.CourseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final CourseRepository courseRepository;
    private final CourseDataService courseDataService; // CourseDataService의 로직 재사용

    public DataInitializer(CourseRepository courseRepository, CourseDataService courseDataService) {
        this.courseRepository = courseRepository;
        this.courseDataService = courseDataService;
    }

    @PostConstruct
    @Transactional
    public void initializeData() {
        if (courseRepository.count() > 0) {
            logger.info("Course 데이터가 이미 DB에 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        logger.info("DB에 Course 데이터가 없습니다. everytime_courses.json에서 데이터를 로드하여 초기화를 시작합니다.");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            ClassPathResource resource = new ClassPathResource("data/everytime_courses.json");
            List<DetailedCourseInfo> loadedCourses = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<>() {}
            );

            // CourseDataService에 있던 원본 로직을 사용하여 데이터 가공
            List<CourseEntity> courseEntities = loadedCourses.stream()
                    .map(this::processAndConvertToEntity) // DetailedCourseInfo를 가공하여 Entity로 변환
                    .collect(Collectors.toList());

            courseRepository.saveAll(courseEntities);
            logger.info("{}개의 강의 정보가 성공적으로 DB에 저장되었습니다.", courseEntities.size());

        } catch (Exception e) {
            logger.error("데이터 초기화 중 심각한 오류 발생", e);
        }
    }

    private CourseEntity processAndConvertToEntity(DetailedCourseInfo dto) {
        // groupId 설정
        String groupId = dto.getCourseCode().length() >= 7 ? dto.getCourseCode().substring(0, 7) : dto.getCourseCode();
        // generalizedType 설정 (CourseDataService의 로직 재활용)
        String generalizedType = courseDataService.determineInitialGeneralizedType(dto.getDepartmentOriginal());
        boolean isRestricted = !(generalizedType.equals("교양") || generalizedType.equals("전공_후보"));

        CourseEntity courseEntity = CourseEntity.builder()
                .courseCode(dto.getCourseCode())
                .courseName(dto.getCourseName())
                .departmentOriginal(dto.getDepartmentOriginal())
                .specificMajor(dto.getSpecificMajor())
                .groupId(groupId)
                .generalizedType(generalizedType)
                .credits(dto.getCredits())
                .totalHours(dto.getTotalHours())
                .grade(dto.getGrade())
                .professor(dto.getProfessor())
                .classroom(dto.getClassroom())
                .remarks(dto.getRemarks())
                .isRestrictedCourse(isRestricted)
                .build();

        if (dto.getScheduleSlots() != null) {
            List<TimeSlotEntity> timeSlots = dto.getScheduleSlots().stream()
                    .flatMap(slotDto -> slotDto.getPeriods().stream().map(period ->
                            TimeSlotEntity.builder()
                                    .course(courseEntity)
                                    .day(slotDto.getDay())
                                    .period(period)
                                    .build()
                    ))
                    .collect(Collectors.toList());
            courseEntity.setScheduleSlots(timeSlots);
        }

        return courseEntity;
    }
}