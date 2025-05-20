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
    private Userservice userService;

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
        sampleUser = UserEntity.builder().Id(Long.valueOf("user1")).grade("2").major("컴퓨터공학부").build();

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
        creditSettings.setCourseTypeCombination(List.of("전공", "교양"));
        creditSettings.setCreditRangesPerType(Map.of(
                "전공", new CreditRangeDto(9, 15),
                "교양", new CreditRangeDto(3, 6)
        ));

        sampleUserPreferences = UserPreferenceEntity.builder()
                .user(sampleUser)
                .timePreferences(timePrefs)
                .creditSettings(creditSettings)
                .build();

        DetailedCourseInfo cs101 = new DetailedCourseInfo("CS101", "전공필수과목", "전공", "전공", 3, 3, "1", "교수A", "101", "기초", List.of(new TimeSlotDto("Mon", List.of(1,2,3))), false);
        DetailedCourseInfo ge101 = new DetailedCourseInfo("GE101", "교양필수1", "교양", "교양", 3, 3, "1", "교수B", "201", "", List.of(new TimeSlotDto("Mon", List.of(4,5,6))), false);
        DetailedCourseInfo ge102 = new DetailedCourseInfo("GE102", "교양선택2", "교양", "교양", 2, 2, "전학년", "교수C", "202", "", List.of(new TimeSlotDto("Tue", List.of(1,2,3))), false);
        DetailedCourseInfo major201 = new DetailedCourseInfo("CS201", "전공선택1", "전공", "전공", 3, 3, "2", "교수D", "301", "", List.of(new TimeSlotDto("Wed", List.of(1,2,3))), false);
        DetailedCourseInfo major202 = new DetailedCourseInfo("CS202", "전공선택2", "전공", "전공", 3, 3, "2", "교수E", "302", "", List.of(new TimeSlotDto("Wed", List.of(4,5,6))), false);
        DetailedCourseInfo noTimeCourse = new DetailedCourseInfo("NT101", "시간정보없는교양", "교양", "교양", 1, 1, "1", "교수F", "미정", "", Collections.emptyList(), false);
        DetailedCourseInfo militaryCourse = new DetailedCourseInfo("MIL101", "군사학1", "군사학", "군사학", 2, 2, "1", "교수G", "군사교육관", "", List.of(new TimeSlotDto("Thu", List.of(7,8))), true);


        sampleAllCourses = new ArrayList<>(List.of(cs101, ge101, ge102, major201, major202, noTimeCourse, militaryCourse));
    }

    @Test
    @DisplayName("기본 시간표 추천 생성 테스트 - 필수 과목 포함")
    void generateRecommendations_WithMandatoryCourse() {
        // Arrange
        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections);
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);
        when(courseDataService.getDetailedCourses()).thenReturn(new ArrayList<>(sampleAllCourses));
        // TimetableService가 DetailedCourseInfo.getGeneralizedType()을 사용하므로,
        // CourseDataService.mapDepartmentToGeneralizedType()에 대한 스터빙은 여기서 필요 없습니다.
        // 만약 TimetableService 내부에서 CourseDataService의 mapDepartmentToGeneralizedType을 명시적으로 호출한다면 필요합니다.
        // (현재 TimetableService 코드는 DetailedCourseInfo에 이미 매핑된 generalizedType을 사용하거나,
        //  헬퍼 메소드 내부에서 courseDataService.mapDepartmentToGeneralizedType을 호출하도록 되어 있습니다.
        //  테스트의 일관성을 위해 TimetableService 내에서 generalizedType을 어떻게 얻는지 확인하고,
        //  만약 courseDataService.mapDepartmentToGeneralizedType을 호출한다면 아래와 같이 모킹합니다.)
        // when(courseDataService.mapDepartmentToGeneralizedType("전공")).thenReturn("전공");
        // when(courseDataService.mapDepartmentToGeneralizedType("교양")).thenReturn("교양");
        // when(courseDataService.mapDepartmentToGeneralizedType("군사학")).thenReturn("군사학"); // 예시
        // when(courseDataService.mapDepartmentToGeneralizedType(null)).thenReturn("기타");

        // Act
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations("user1");

        // Assert
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
                if(typeRanges != null && typeRanges.containsKey(type)){ // typeRanges가 null이 아니고, 해당 타입에 대한 설정이 있을 때만 검증
                    assertTrue(actualCredits >= typeRanges.get(type).getMin(), type + " 유형 최소 학점 미달: 실제 " + actualCredits + ", 최소 " + typeRanges.get(type).getMin());
                    assertTrue(actualCredits <= typeRanges.get(type).getMax(), type + " 유형 최대 학점 초과: 실제 " + actualCredits + ", 최대 " + typeRanges.get(type).getMax());
                }
            }
        } else {
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
        // 테스트에 필요한 최소 강의만 포함 (CourseDataService가 이 리스트를 반환하도록 모킹)
        when(courseDataService.getDetailedCourses()).thenReturn(List.of(cs101Conflict, cs102MandatoryConflict, sampleAllCourses.get(1)));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            timetableService.generateRecommendations("user1");
        });
        assertTrue(exception.getMessage().contains("필수 또는 재수강 과목 간에 시간이 중복됩니다."));
    }

    // ... (다른 테스트 케이스들)
}