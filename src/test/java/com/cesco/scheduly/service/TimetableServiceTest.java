package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
// import static org.mockito.Mockito.lenient; // lenient 사용 시

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private CourseDataService courseDataService; // TimetableService가 CourseDataService를 사용함

    @InjectMocks
    private TimetableService timetableService;

    private UserEntity sampleUser;
    private UserCourseSelectionEntity sampleUserSelections;
    private UserPreferenceEntity sampleUserPreferences;
    private List<DetailedCourseInfo> sampleAllCourses;

    @BeforeEach
    void setUp() {
        sampleUser = UserEntity.builder().userId("user1").grade("2").major("컴퓨터공학부").build();

        List<String> mandatoryCodes = List.of("CS101");
        List<String> takenCodes = List.of("OLD101");
        List<String> retakeCodes = new ArrayList<>();

        sampleUserSelections = UserCourseSelectionEntity.builder()
                .user(sampleUser)
                .mandatoryCourses(mandatoryCodes)
                .takenCourses(takenCodes)
                .retakeCourses(retakeCodes)
                .build();

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(15);
        creditSettings.setMaxTotalCredits(21);
        creditSettings.setCourseTypeCombination(List.of("전공", "교양")); // 이 타입들은 DetailedCourseInfo의 generalizedType과 일치해야 함
        creditSettings.setCreditRangesPerType(Map.of(
                "전공", new CreditRangeDto(9, 15),
                "교양", new CreditRangeDto(3, 6)
        ));

        sampleUserPreferences = UserPreferenceEntity.builder()
                .user(sampleUser)
                .timePreferences(timePrefs)
                .creditSettings(creditSettings)
                .build();

        // DetailedCourseInfo 생성 시 generalizedType을 CourseDataService의 실제 로직과 유사하게 설정
        DetailedCourseInfo cs101 = new DetailedCourseInfo("CS101", "전공필수과목", "전공", "전공", 3, 3, "1", "교수A", "101", "기초", List.of(new TimeSlotDto("Mon", List.of(1,2,3))), false);
        DetailedCourseInfo ge101 = new DetailedCourseInfo("GE101", "교양필수1", "교양", "교양", 3, 3, "1", "교수B", "201", "", List.of(new TimeSlotDto("Mon", List.of(4,5,6))), false);
        DetailedCourseInfo ge102 = new DetailedCourseInfo("GE102", "교양선택2", "교양", "교양", 2, 2, "전학년", "교수C", "202", "", List.of(new TimeSlotDto("Tue", List.of(1,2,3))), false);
        DetailedCourseInfo major201 = new DetailedCourseInfo("CS201", "전공선택1", "전공", "전공", 3, 3, "2", "교수D", "301", "", List.of(new TimeSlotDto("Wed", List.of(1,2,3))), false);
        DetailedCourseInfo major202 = new DetailedCourseInfo("CS202", "전공선택2", "전공", "전공", 3, 3, "2", "교수E", "302", "", List.of(new TimeSlotDto("Wed", List.of(4,5,6))), false);
        DetailedCourseInfo noTimeCourse = new DetailedCourseInfo("NT101", "시간정보없는교양", "교양", "교양", 1, 1, "1", "교수F", "미정", "", Collections.emptyList(), false);
        // 특수 과목 예시 (isRestrictedCourse=true, generalizedType도 해당 값으로)
        DetailedCourseInfo militaryCourse = new DetailedCourseInfo("MIL101", "군사학1", "군사학", "군사학", 2, 2, "1", "교수G", "군사교육관", "", List.of(new TimeSlotDto("Thu", List.of(7,8))), true);


        sampleAllCourses = new ArrayList<>(List.of(cs101, ge101, ge102, major201, major202, noTimeCourse, militaryCourse));
    }

    @Test
    @DisplayName("기본 시간표 추천 생성 테스트 - 필수 과목 포함")
    void generateRecommendations_WithMandatoryCourse() {
        // Arrange
        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections); // CS101이 필수
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);
        when(courseDataService.getDetailedCourses()).thenReturn(new ArrayList<>(sampleAllCourses));

        // TimetableService가 DetailedCourseInfo의 getGeneralizedType()을 사용한다고 가정하면,
        // courseDataService.mapDepartmentToGeneralizedType()에 대한 스터빙은 여기서 필요 없습니다.
        // 만약 TimetableService가 이 메소드를 *직접* 호출하는 부분이 있다면, 그 호출에 대해서만 스터빙합니다.
        // 예를 들어, TimetableService의 calculateCreditsByType에서 generalizedType을 다시 계산하기 위해 호출한다면,
        // 그 때 필요한 스터빙만 남깁니다. 현재 TimetableService 코드는 DetailedCourseInfo.getGeneralizedType()을 사용합니다.

        // Act
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations("user1");

        // Assert (이전과 동일 또는 더 상세하게)
        assertNotNull(recommendations);
        if (!recommendations.isEmpty()) {
            RecommendedTimetableDto firstRecommendation = recommendations.get(0);
            System.out.println("추천된 시간표 ID: " + firstRecommendation.getTimetableId());
            firstRecommendation.getScheduledCourses().forEach(course ->
                    System.out.println(String.format("  - %s (%s): %s학점, 타입:%s, 시간:%s", course.getCourseName(), course.getCourseCode(), course.getCredits(), course.getDepartment(), course.getActualClassTimes()))
            );
            System.out.println("  총 학점: " + firstRecommendation.getTotalCredits());
            System.out.println("  유형별 학점: " + firstRecommendation.getCreditsByType());

            assertTrue(firstRecommendation.getScheduledCourses().stream()
                    .anyMatch(c -> "CS101".equals(c.getCourseCode())), "필수 과목 CS101이 포함되어야 합니다.");

            CreditSettingsRequest settings = sampleUserPreferences.getCreditSettings();
            assertTrue(firstRecommendation.getTotalCredits() >= settings.getMinTotalCredits(), "최소 총 학점(" + settings.getMinTotalCredits() + ") 미달: " + firstRecommendation.getTotalCredits());
            assertTrue(firstRecommendation.getTotalCredits() <= settings.getMaxTotalCredits(), "최대 총 학점(" + settings.getMaxTotalCredits() + ") 초과: " + firstRecommendation.getTotalCredits());

            Map<String, CreditRangeDto> typeRanges = settings.getCreditRangesPerType();
            for(Map.Entry<String, Integer> entry : firstRecommendation.getCreditsByType().entrySet()){
                String type = entry.getKey();
                Integer actualCredits = entry.getValue();
                if(typeRanges.containsKey(type)){ // 사용자가 설정한 유형에 대해서만 검증
                    assertTrue(actualCredits >= typeRanges.get(type).getMin(), type + " 유형 최소 학점 미달: 실제 " + actualCredits + ", 최소 " + typeRanges.get(type).getMin());
                    assertTrue(actualCredits <= typeRanges.get(type).getMax(), type + " 유형 최대 학점 초과: 실제 " + actualCredits + ", 최대 " + typeRanges.get(type).getMax());
                }
            }
        } else {
            // 이 테스트 케이스는 CS101(3학점, 전공)이 필수이고,
            // CreditSettings에서 전공 최소 9학점, 교양 최소 3학점, 전체 최소 15학점을 요구하므로,
            // CS101만으로는 조건을 만족하지 못해 추가 과목이 선택되어야 합니다.
            // 따라서 추천이 나와야 정상입니다. 만약 비어있다면 알고리즘 문제일 수 있습니다.
            fail("기본 시나리오에서 추천 시간표가 생성되지 않았습니다. 알고리즘 또는 데이터 설정을 확인하세요.");
        }
    }

    @Test
    @DisplayName("필수 과목 간 시간 중복 시 예외 발생 테스트")
    void generateRecommendations_MandatoryCoursesConflict() {
        // Arrange
        DetailedCourseInfo cs101Conflict = new DetailedCourseInfo("CS101", "전공필수과목", "전공", "전공", 3, 3, "1", "교수A", "101", "기초", List.of(new TimeSlotDto("Mon", List.of(1,2,3))), false);
        DetailedCourseInfo cs102MandatoryConflict = new DetailedCourseInfo("CS102M", "필수충돌과목", "전공", "전공", 3, 3, "1", "교수X", "102", "", List.of(new TimeSlotDto("Mon", List.of(2,3,4))), false);

        sampleUserSelections.setMandatoryCourses(List.of("CS101", "CS102M"));

        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections);
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);
        when(courseDataService.getDetailedCourses()).thenReturn(List.of(cs101Conflict, cs102MandatoryConflict, sampleAllCourses.get(1))); // 테스트에 필요한 최소 강의만 포함

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            timetableService.generateRecommendations("user1");
        });
        assertTrue(exception.getMessage().contains("필수 또는 재수강 과목 간에 시간이 중복됩니다."));
    }

    // ... (다른 테스트 케이스들)
}