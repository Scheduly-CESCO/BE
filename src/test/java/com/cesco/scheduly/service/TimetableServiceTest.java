package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.exception.MandatoryCourseConflictException;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // UnnecessaryStubbingException 경고를 무시하고 테스트를 유연하게 만듭니다.
class TimetableServiceTest {

    @InjectMocks
    private TimetableService timetableService;

    @Mock
    private UserService userService;

    @Mock
    private CourseDataService courseDataService;

    private User testUser;
    private final Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        // 1. 테스트에 사용할 가짜 사용자(User) 객체 생성
        testUser = User.builder()
                .id(testUserId)
                .studentId("20210001") // 4학년 학번
                .name("극한테스트유저")
                .grade(4)
                .major("Global Business & Technology전공")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("AI데이터융합전공")
                .college(College.경상대학)
                .build();

        // TimetableService가 내부적으로 사용하는 ObjectMapper 주입
        ReflectionTestUtils.setField(timetableService, "objectMapper", new ObjectMapper());
    }

    // =====================================================================================
    // 기존 테스트 케이스들...
    // =====================================================================================

    @Test
    @DisplayName("1. 필수/재수강 과목이 시간표에 정상적으로 포함되는지 검증")
    void should_always_include_mandatory_and_retake_courses() {
        // given: 필수 과목과 재수강 과목만 설정
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("M01201101")); // 통계모델링(필수)
        selections.setRetakeCourses(List.of("M01301101")); // 비정형데이터마이닝(재수강)

        UserPreferenceEntity preferences = createDefaultPreferences(); // 기본 학점/시간 설정

        // Mock 설정
        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> mockCourseList = createSimpleCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(mockCourseList);
        mockCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        RecommendedTimetableDto firstTimetable = recommendations.get(0);
        List<String> recommendedCourseCodes = firstTimetable.getScheduledCourses().stream()
                .map(ScheduledCourseDto::getCourseCode)
                .toList();

        assertThat(recommendedCourseCodes).contains("M01201101", "M01301101");
    }

    @Test
    @DisplayName("4. 필수 과목 간 시간이 겹칠 경우 MandatoryCourseConflictException 예외 발생 검증")
    void should_throw_exception_when_mandatory_courses_conflict() {
        // given
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("M01201101", "CONFLICT01"));

        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(createDefaultPreferences());

        List<DetailedCourseInfo> mockCourseList = createSimpleCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(mockCourseList);
        mockCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when & then
        assertThrows(MandatoryCourseConflictException.class, () -> {
            timetableService.generateRecommendations(testUserId);
        }, "필수 과목 간 시간이 중복되므로 예외가 발생해야 합니다.");
    }

    @Test
    @DisplayName("3. 기수강 과목이 추천 후보에서 정상적으로 제외되는지 검증")
    void should_exclude_taken_courses_from_recommendation() {
        // given
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setTakenCourses(new ArrayList<>(List.of("V41010101")));
        selections.setRetakeCourses(List.of("M01301101"));
        selections.getTakenCourses().add("V41010102");

        UserPreferenceEntity preferences = createDefaultPreferences();

        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> mockCourseList = createSimpleCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(mockCourseList);
        mockCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        List<String> recommendedCourseCodes = recommendations.get(0).getScheduledCourses().stream()
                .map(ScheduledCourseDto::getCourseCode)
                .toList();

        assertThat(recommendedCourseCodes)
                .doesNotContain("V41010101", "V41010103")
                .contains("M01301101");
    }

    @Test
    @DisplayName("5. 교양 과목이 정상적으로 '교양'으로 분류되는지 검증")
    void should_classify_liberal_arts_course_correctly() {
        // given
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        UserPreferenceEntity preferences = createDefaultPreferences();
        preferences.getCreditSettings().setCreditGoalsPerType(Map.of("교양", new CreditRangeDto(3, 3)));

        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> courseList = List.of(
                createCourse("Y13115302", "대학중국어1", null, "교양", "교양", 3, new TimeSlotDto("Mon", List.of(1, 2)))
        );
        given(courseDataService.getDetailedCourses()).willReturn(courseList);

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        Map<String, Integer> creditsByType = recommendations.get(0).getCreditsByType();
        assertThat(creditsByType.get("교양")).isEqualTo(3);
    }

    @Test
    @DisplayName("6. 사용자의 전공이 아닌 과목이 '자선'으로 정상 분류되는지 검증")
    void should_classify_other_department_major_course_as_elective() {
        // given
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        UserPreferenceEntity preferences = createDefaultPreferences();
        preferences.getCreditSettings().setCreditGoalsPerType(Map.of("자선", new CreditRangeDto(3, 3)));

        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> courseList = List.of(
                createCourse("F05301201", "프로그래밍언어론", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(1, 2)))
        );
        given(courseDataService.getDetailedCourses()).willReturn(courseList);

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        Map<String, Integer> creditsByType = recommendations.get(0).getCreditsByType();
        assertThat(creditsByType.get("자선")).isEqualTo(3);
    }

    @Test
    @DisplayName("7. 모든 필터링(필수/기수강/시간/학점) 적용 후 최종 시간표 추천 성공 검증")
    void should_generate_valid_timetable_with_all_constraints() {
        // given
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setTakenCourses(List.of("V41002201"));
        selections.setMandatoryCourses(List.of("M01201101"));
        selections.setRetakeCourses(List.of("M01301101"));

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(4, 5, 6, 7)),
                new TimeSlotDto("Wed", List.of(4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Tue", List.of(4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Thu", List.of(4, 5, 6, 7, 8, 9))
        ));

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(15);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(6, 9),
                "이중전공", new CreditRangeDto(6, 6),
                "교양", new CreditRangeDto(3, 3)
        ));

        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> mockCourseList = createFullMockCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(mockCourseList);
        mockCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();

        RecommendedTimetableDto firstTimetable = recommendations.get(0);
        List<String> recommendedCourseCodes = firstTimetable.getScheduledCourses().stream()
                .map(ScheduledCourseDto::getCourseCode)
                .toList();

        assertThat(recommendedCourseCodes).contains("M01201101").contains("M01301101").doesNotContain("V41002201");

        Map<String, Integer> creditsByType = firstTimetable.getCreditsByType();
        assertThat(firstTimetable.getTotalCredits()).isBetween(15, 18);
        assertThat(creditsByType.get("전공")).isBetween(6, 9);
        assertThat(creditsByType.get("이중전공")).isEqualTo(6);
        assertThat(creditsByType.get("교양")).isEqualTo(3);

        firstTimetable.getScheduledCourses().forEach(course -> {
            course.getActualClassTimes().forEach(slot -> {
                assertThat(slot.getDay()).isIn("Mon", "Tue", "Wed", "Thu");
                assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(4);
            });
        });

        for (int i = 0; i < recommendations.size(); i++) {
            System.out.printf("====== [Test 8] Recommended Timetable #%d ======\n", i + 1);
            recommendations.get(i).getScheduledCourses().forEach(c -> System.out.printf("- %s (%s, %s) - %s | %s\n", c.getCourseName(), c.getCourseCode(), c.getDepartment(), c.getActualClassTimes(), c.getCredits()));
            System.out.println("============================================");
        }
    }
    @Test
    @DisplayName("8. [실 데이터] 금공강/오전수업X/빡빡한 학점&필수과목 조건에서 3개 이상 시간표 추천 검증")
    void should_recommend_at_least_3_timetables_with_real_data_under_extreme_conditions() {
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setTakenCourses(List.of("V41002201", "D01205A01", "V41010101", "M01201101"));
        selections.setMandatoryCourses(List.of("M01207101", "P05406201"));
        selections.setRetakeCourses(List.of("M01301101"));
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(2, 3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Tue", List.of(2, 3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Thu", List.of(2, 3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Wed", List.of(5, 6, 7, 8, 9, 10, 11, 12))
        ));
        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(18);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 9),
                "이중전공", new CreditRangeDto(6, 6),
                "교양", new CreditRangeDto(3, 3)
        ));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);
        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);
        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        assertThat(recommendations).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < recommendations.size(); i++) {
            System.out.printf("====== [Test 8] Recommended Timetable #%d ======\n", i + 1);
            recommendations.get(i).getScheduledCourses().forEach(c -> System.out.printf("- %s (%s, %s) - %s | %s\n", c.getCourseName(), c.getCourseCode(), c.getDepartment(), c.getActualClassTimes(), c.getCredits()));
            System.out.println("============================================");
        }
    }

    @Test
    @DisplayName("9. [시나리오 2] 스페인어/AI 이중전공 3학년, 금공강 & 17학점 & 오후수업 선호")
    void should_recommend_timetables_for_spanish_major_with_no_friday_classes() {
        User spanishUser = User.builder()
                .id(testUserId).studentId("20220001").name("스페인어AI테스트유저").grade(3)
                .major("스페인어통번역학과").doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("AI융합전공(Software&AI)").college(College.통번역대학).build();
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("A05303203", "V41008201"));
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(4,5,6,7,8,9)), new TimeSlotDto("Tue", List.of(4,5,6,7,8,9)),
                new TimeSlotDto("Wed", List.of(4,5,6,7,8,9)), new TimeSlotDto("Thu", List.of(4,5,6,7,8,9))
        ));
        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(15);
        creditSettings.setMaxTotalCredits(20);
        creditSettings.setCreditGoalsPerType(Map.of("전공", new CreditRangeDto(7, 9), "이중전공", new CreditRangeDto(6, 6), "교양", new CreditRangeDto(2, 5)));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);
        given(userService.getUserDetails(testUserId)).willReturn(spanishUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);
        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        assertThat(recommendations).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < recommendations.size(); i++) {
            System.out.printf("====== [Test 9] Recommended Timetable #%d ======\n", i + 1);
            recommendations.get(i).getScheduledCourses().forEach(c -> System.out.printf("- %s (%s, %s) - %s | %s\n", c.getCourseName(), c.getCourseCode(), c.getDepartment(), c.getActualClassTimes(), c.getCredits()));
            System.out.println("============================================");
        }
    }

    @Test
    @DisplayName("10. [시나리오 3] 러시아/AI 이중전공 2학년, 수공강 & 18학점 & 오전수업 선호")
    void should_recommend_timetables_for_russian_major_with_no_wednesday_classes() {
        User russianUser = User.builder()
                .id(testUserId).studentId("20230001").name("러시아AI테스트유저").grade(2)
                .major("러시아학과").doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("AI융합전공(Software&AI)").college(College.국제지역대학).build();
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("A04223201", "A04359201"));
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(1,2,3,4,5,6,7, 8,9)), new TimeSlotDto("Tue", List.of(1,2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Thu", List.of(1,2,3,4,5,6,7,8,9)), new TimeSlotDto("Fri", List.of(1,2,3,4,5,6,7,8,9))
        ));
        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(14);
        creditSettings.setMaxTotalCredits(20);
        creditSettings.setCreditGoalsPerType(Map.of("전공", new CreditRangeDto(6, 9), "이중전공", new CreditRangeDto(6, 9), "교양", new CreditRangeDto(2, 2)));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);
        given(userService.getUserDetails(testUserId)).willReturn(russianUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);
        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);
        assertThat(recommendations).isNotEmpty();

        assertThat(recommendations).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < recommendations.size(); i++) {
            System.out.printf("====== [Test 10] Recommended Timetable #%d ======\n", i + 1);
            recommendations.get(i).getScheduledCourses().forEach(c -> System.out.printf("- %s (%s, %s) - %s | %s\n", c.getCourseName(), c.getCourseCode(), c.getDepartment(), c.getActualClassTimes(), c.getCredits()));
            System.out.println("============================================");
        }
    }

    @Test
    @DisplayName("11. [시나리오 4] 컴공/GBT 부전공 3학년, 월공강 & 21학점 & 복잡한 시간 선호")
    void should_recommend_timetables_for_cs_major_with_21_credits_and_complex_time_prefs() {
        User csUser = User.builder()
                .id(testUserId).studentId("20220002").name("컴공GBT테스트유저").grade(3)
                .major("컴퓨터공학전공").doubleMajorType(DoubleMajorType.MINOR)
                .doubleMajor("Global Business & Technology전공").college(College.공과대학).build();
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("V44301601", "D10405701"));
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Tue", List.of(3,4,5,6,7,8,9)), new TimeSlotDto("Wed", List.of(6,7,8,9)),
                new TimeSlotDto("Thu", List.of(1,2,3,4,5,6,7,8,9)), new TimeSlotDto("Fri", List.of(3,4,5,6,7,8,9))
        ));
        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(18);
        creditSettings.setMaxTotalCredits(21);
        creditSettings.setCreditGoalsPerType(Map.of("전공", new CreditRangeDto(6, 9), "부전공", new CreditRangeDto(6, 9), "교양", new CreditRangeDto(1, 3)));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);
        given(userService.getUserDetails(testUserId)).willReturn(csUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);
        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);
        assertThat(recommendations).isNotEmpty();

        assertThat(recommendations).hasSizeGreaterThanOrEqualTo(3);

        for (int i = 0; i < recommendations.size(); i++) {
            System.out.printf("====== [Test 11] Recommended Timetable #%d ======\n", i + 1);
            recommendations.get(i).getScheduledCourses().forEach(c -> System.out.printf("- %s (%s, %s) - %s | %s\n", c.getCourseName(), c.getCourseCode(), c.getDepartment(), c.getActualClassTimes(), c.getCredits()));
            System.out.println("============================================");
        }
    }

    // =====================================================================================
    // 테스트 데이터 생성 헬퍼 메소드
    // =====================================================================================
    private List<DetailedCourseInfo> createSimpleCourseList() {
        return List.of(
                createCourse("V41010101", "고급파이썬프로그래밍", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("V41010103", "고급파이썬프로그래밍", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(1, 2, 3))),
                createCourse("M01201101", "통계모델링", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4, 5, 6))),
                createCourse("CONFLICT01", "시간겹치는과목", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(5, 6, 7))),
                createCourse("M01301101", "비정형데이터마이닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6)))
        );
    }

    private List<DetailedCourseInfo> createFullMockCourseList() {
        return List.of(
                createCourse("V41002201", "컴퓨터수학", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("M01201101", "통계모델링", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4, 5, 6))),
                createCourse("M01301101", "비정형데이터마이닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("M01202101", "자료구조와알고리즘", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4,5,6))),
                createCourse("T07403201", "종합설계", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(7, 8, 9))),
                createCourse("D01205A02", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                createCourse("D01205A03", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("P05201101", "데이터구조", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7,8,9))),
                createCourse("D10405701", "마케팅관리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7,8,9))),
                createCourse("Y13115302", "대학중국어1", null, "교양", "교양", 3, new TimeSlotDto("Wed", List.of(7, 8,9))),
                createCourse("Y51221101", "인공지능과마음", null, "교양", "교양", 2, new TimeSlotDto("Wed", List.of(7, 8))),
                createCourse("U51512201", "체육(야영생활과리더쉽)", null, "교양", "교양", 1, new TimeSlotDto("Wed", List.of(5, 6))),
                createCourse("P05412101", "비즈니스머신러닝", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("P05309101", "빅데이터분석", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(1, 2, 3)))
        );
    }

    private List<DetailedCourseInfo> createRealisticExtremeCourseList() {
        return List.of(
                // --- 기수강 과목 ---
                createCourse("V41002201", "컴퓨터수학", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("D01205A01", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("V41010101", "고급파이썬프로그래밍", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("M01201101", "통계모델링", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4, 5, 6))),

                // --- 시나리오별 필수 과목 ---
                createCourse("M01207101", "머신러닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                createCourse("P05406201", "졸업프로젝트실습(캡스톤디자인)", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(10, 11, 12))),
                createCourse("M01301101", "비정형데이터마이닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("A05303203", "고급스페인어작문(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(5, 6))),
                createCourse("V41008201", "알고리즘", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("A04223201", "러시아어말하기심화(1)", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(1, 2))),
                createCourse("A04359201", "러시아어문장론", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(3, 4))),
                createCourse("T07201201", "컴퓨터논리개론", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7,8,9))),
                createCourse("V44301601", "컴퓨터구조", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(5,6))),
                createCourse("D10405701", "마케팅관리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7, 8, 9))),
                createCourse("A07377201", "러시아역사(1)", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(1, 2))),

                // [AI / Software & AI]
                createCourse("M01202101", "자료구조와알고리즘", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("V41020101", "인공지능", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("T07403201", "종합설계", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(7, 8, 9))),
                createCourse("T02253601", "웹프로그래밍", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("V41009201", "운영체제", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(1, 2, 3))),

                // [GBT & 국제금융 & 경상대학]
                createCourse("P05201101", "데이터구조", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7, 8, 9))),
                createCourse("P05304201", "데이터베이스", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("P05307101", "인적자원관리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4,5,6))),
                createCourse("P01309201", "국제마케팅", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                createCourse("D03467502", "국제경영전략", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("P05208101", "객체지향프로그래밍", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4,5,6))),
                createCourse("P01317101", "화폐금융론", "국제금융학과", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4,5,6))),
                createCourse("Q02009102", "경제학개론", "경상대학", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4,5,6))),
                createCourse("D01311601", "재무관리", "국제금융학과", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(1,2,3))),

                // [컴퓨터공학]
                createCourse("T05479201", "설계패턴", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(2,3,4))),
                createCourse("T05402201", "데이터베이스설계", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(6,7,8))),
                createCourse("T01412201", "컴퓨터비전개론", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(1,2,3))),
                createCourse("T01301601", "디지털신호처리", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(7,8))),
                createCourse("V44354401", "시스템프로그래밍", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(7,8,9))),
                createCourse("T01402201", "게임프로그래밍", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(3,4,5))),
                createCourse("F05462301", "컴퓨터그래픽스", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(5,6))),
                createCourse("T04351601", "데이터통신", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(8,9))),
                createCourse("T05306201", "논리회로", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(6,7))),

                // [스페인어]
                createCourse("N05309101", "스페인어문학번역", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Tue", List.of(7, 8))),
                createCourse("A05301104", "고급스페인어회화(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(3, 4))),
                createCourse("N05307101", "스페인어문장구역", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Tue", List.of(5, 6))),
                createCourse("A05301105", "고급스페인어회화(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(5, 6))),
                createCourse("N05321201", "이베로아메리카지역음악과미술(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Tue", List.of(7, 8))),
                createCourse("A05401101", "스페인어고급문법세미나(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(3, 4))),
                createCourse("N05405101", "스페인어순차통역(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Wed", List.of(3,4))),

                // [러시아어]
                createCourse("A04215201", "러시아어텍스트분석(1)", "러시아학과", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(1, 2, 3))),
                createCourse("A04301401", "19세기러시아문학과사회", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Wed", List.of(5, 6))),
                createCourse("B07371201", "인도지역연구(1)", "인도학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(5,6))),
                createCourse("A04321201", "러시아어통역연습", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(5, 6))),

                // [교양]
                createCourse("Y13115302", "대학중국어1", null, "교양", "교양", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("U76269201", "광고.PR의이해", null, "교양", "교양", 2, new TimeSlotDto("Thu", List.of(3, 4))),
                createCourse("Y51215301", "도시의미학과문화", null, "교양", "교양", 2, new TimeSlotDto("Fri", List.of(5, 6))),
                createCourse("Y91147101", "위기관리와국가정보", null, "교양", "교양", 2, new TimeSlotDto("Tue", List.of(7, 8))),
                createCourse("U51519101", "현대인의정신건강", null, "교양", "교양", 2, new TimeSlotDto("Mon", List.of(5, 6))),
                createCourse("Y91163201", "한국글로벌기업마케팅", null, "교양", "교양", 2, new TimeSlotDto("Tue", List.of(3, 4))),
                createCourse("U51512201", "체육(야영생활과리더쉽)", null, "교양", "교양", 1, new TimeSlotDto("Wed", List.of(5, 6))),
                createCourse("U51425201", "빛과물질", null, "교양", "교양", 2, new TimeSlotDto("Mon", List.of(3,4))),
                createCourse("Y51225101", "한시로읽는동양의삶과미학", null, "교양", "교양", 2, new TimeSlotDto("Wed", List.of(1,2))),
                createCourse("U51108201", "신화와종교", null, "교양", "교양", 2, new TimeSlotDto("Mon", List.of(7,8))),
                createCourse("U76272201", "국제화시대의경영", null, "교양", "교양", 2, new TimeSlotDto("Tue", List.of(1,2))),
                createCourse("U76273201", "기업회계의기초원리", null, "교양", "교양", 2, new TimeSlotDto("Wed", List.of(3,4))),
                createCourse("Y21185101", "현대사회와보험", null, "교양", "교양", 2, new TimeSlotDto("Fri", List.of(5,6))),


                // --- 제약조건에 걸리는 '함정' 과목들 ---
                createCourse("P05309101", "빅데이터분석", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(4, 5, 6))),
                createCourse("P05412101", "비즈니스머신러닝", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("V41007201", "데이터사이언스", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(1, 2, 3)))


        );

    }

    private DetailedCourseInfo createCourse(String code, String name, String specificMajor, String deptOriginal, String generalizedType, int credits, TimeSlotDto time) {
        DetailedCourseInfo course = new DetailedCourseInfo();
        course.setCourseCode(code);
        course.setCourseName(name);
        course.setSpecificMajor(specificMajor);
        course.setDepartmentOriginal(deptOriginal);
        course.setGeneralizedType(generalizedType);
        course.setCredits(credits);
        course.setGroupId(code.length() >= 7 ? code.substring(0, 7) : code);
        course.setScheduleSlots(List.of(time));
        return course;
    }

    private UserPreferenceEntity createDefaultPreferences() {
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(new TimePreferenceRequest());

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(1);
        creditSettings.setMaxTotalCredits(21);
        preferences.setCreditSettings(creditSettings);

        return preferences;
    }
}
