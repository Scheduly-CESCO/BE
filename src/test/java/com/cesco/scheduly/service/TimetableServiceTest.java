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
                .major("AI데이터융합전공")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("Global Business & Technology전공")
                .college(College.AI융합대학)
                .build();

        // TimetableService가 내부적으로 사용하는 ObjectMapper 주입
        ReflectionTestUtils.setField(timetableService, "objectMapper", new ObjectMapper());
    }

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
        selections.setTakenCourses(List.of("V41002201")); //기수강
        selections.setMandatoryCourses(List.of("M01201101")); //필수
        selections.setRetakeCourses(List.of("M01301101")); //재수강

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
    }

    // =====================================================================================
    // >> 신규 추가: 실제 데이터 기반 극한 조건 테스트 케이스
    // =====================================================================================

    @Test
    @DisplayName("8. [실 데이터] 금공강/오전수업X/빡빡한 학점&필수과목 조건에서 3개 이상 시간표 추천 검증")
    void should_recommend_at_least_3_timetables_with_real_data_under_extreme_conditions() {
        // given: 1. 실제 데이터를 바탕으로 한 매우 까다로운 사용자 설정 정의

        // - 기수강/필수/재수강 과목 설정 (실제 학수번호 사용)
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setTakenCourses(List.of("V41002201", "D01205A01", "V41010101", "M01201101")); // 컴수, 회계원리, 고파썬, 통계모델링 등
        selections.setMandatoryCourses(List.of("M01207101", "P05406201")); // [전공필수]머신러닝, [이중필수]졸업프로젝트실습
        selections.setRetakeCourses(List.of("M01301101")); // [전공재수강]비정형데이터마이닝

        // - 시간 선호도 설정 (금공강, 1교시X, 수요일 오후만)
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Tue", List.of(2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Thu", List.of(2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Wed", List.of(5,6,7,8,9,10,11,12))
        ));

        // - 학점 목표 설정 (18학점)
        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(18);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 9),      // 전공 9학점 (필수/재수강 6 + 선택 3)
                "이중전공", new CreditRangeDto(6, 6), // 이중전공 6학점 (필수 3 + 선택 3)
                "교양", new CreditRangeDto(3, 3)      // 교양 3학점
        ));

        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        // 2. Mock 서비스 설정
        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        // - 이 시나리오를 통과할 수 있도록 실제 데이터 기반의 강의 목록 생성
        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when: 시간표 추천 기능 실행
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then: 모든 극한 조건을 만족시키면서 최소 3개 이상의 시간표가 추천되었는지 검증
        assertThat(recommendations).hasSizeGreaterThanOrEqualTo(3);

        // 추천된 모든 시간표가 조건을 만족하는지 반복 검증
        for (RecommendedTimetableDto timetable : recommendations) {
            List<String> courseCodes = timetable.getScheduledCourses().stream().map(ScheduledCourseDto::getCourseCode).toList();

            // - 필수/재수강/기수강 검증
            assertThat(courseCodes).contains("M01207101", "M01301101", "P05406201");
            assertThat(courseCodes).doesNotContain("V41002201", "D01205A01");

            // - 학점 목표 검증
            assertThat(timetable.getTotalCredits()).isEqualTo(18);
            Map<String, Integer> creditsByType = timetable.getCreditsByType();
            assertThat(creditsByType.get("전공")).isEqualTo(9);
            assertThat(creditsByType.get("이중전공")).isEqualTo(6);
            assertThat(creditsByType.get("교양")).isEqualTo(3);

            // - 시간 선호도 검증
            timetable.getScheduledCourses().forEach(course -> {
                course.getActualClassTimes().forEach(slot -> {
                    assertThat(slot.getDay()).isNotEqualTo("Fri"); // 금공강 확인
                    if (slot.getDay().equals("Wed")) {
                        assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(5); // 수요일 오후 수업 확인
                    } else {
                        assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(2); // 나머지 요일 1교시 제외 확인
                    }
                });
            });
        }
    }

    // =====================================================================================
    // >> 신규 추가: 시나리오 2
    // =====================================================================================
    @Test
    @DisplayName("9. [시나리오 2] 스페인어/AI 이중전공 3학년, 금공강 & 18학점 & 오후수업 선호")
    void should_recommend_timetables_for_spanish_major_with_no_friday_classes() {
        // given: 1. 사용자 정보 및 선호도 설정
        User spanishUser = User.builder()
                .id(testUserId)
                .studentId("20220001")
                .name("스페인어AI테스트유저")
                .grade(3)
                .major("스페인어통번역학과")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("AI융합전공(Software&AI)")
                .college(College.통번역대학)
                .build();

        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("A05303202", "V41008201")); // 고급스페인어작문(1), 알고리즘

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(4,5,6,7,8,9)),
                new TimeSlotDto("Tue", List.of(4,5,6,7,8,9)),
                new TimeSlotDto("Wed", List.of(4,5,6,7,8,9)),
                new TimeSlotDto("Thu", List.of(4,5,6,7,8,9))
        ));

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(18);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 9),      // 전공 9학점 (필수/재수강 6 + 선택 3)
                "이중전공", new CreditRangeDto(6, 6), // 이중전공 6학점 (필수 3 + 선택 3)
                "교양", new CreditRangeDto(3, 3)      // 교양 3학점
        ));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        // 2. Mock 서비스 설정
        given(userService.getUserDetails(testUserId)).willReturn(spanishUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        recommendations.forEach(timetable -> {
            assertThat(timetable.getTotalCredits()).isEqualTo(18);
            assertThat(timetable.getCreditsByType().get("전공")).isEqualTo(9);
            assertThat(timetable.getCreditsByType().get("이중전공")).isEqualTo(6);
            timetable.getScheduledCourses().forEach(course -> {
                course.getActualClassTimes().forEach(slot -> {
                    assertThat(slot.getDay()).isNotEqualTo("Fri");
                    assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(4);
                });
            });
        });
    }

    // =====================================================================================
    // >> 신규 추가: 시나리오 3
    // =====================================================================================
    @Test
    @DisplayName("10. [시나리오 3] 러시아/AI 이중전공 2학년, 수공강 & 18학점 & 오전수업 선호")
    void should_recommend_timetables_for_russian_major_with_no_wednesday_classes() {
        // given: 1. 사용자 정보 및 선호도 설정
        User russianUser = User.builder()
                .id(testUserId)
                .studentId("20230001")
                .name("러시아AI테스트유저")
                .grade(2)
                .major("러시아학과")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("AI융합전공(Software&AI)")
                .college(College.국제지역대학)
                .build();

        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("A04223201", "T07201201")); // 러시아어말하기심화(1), 컴퓨터논리개론

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(1,2,3,4,5,6,7)),
                new TimeSlotDto("Tue", List.of(1,2,3,4,5,6,7)),
                new TimeSlotDto("Thu", List.of(1,2,3,4,5,6,7)),
                new TimeSlotDto("Fri", List.of(1,2,3,4,5,6,7))
        ));

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(18);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 9),      // 전공 9학점 (필수/재수강 6 + 선택 3)
                "이중전공", new CreditRangeDto(6, 6), // 이중전공 6학점 (필수 3 + 선택 3)
                "교양", new CreditRangeDto(3, 3)      // 교양 3학점
        ));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        // 2. Mock 서비스 설정
        given(userService.getUserDetails(testUserId)).willReturn(russianUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        recommendations.forEach(timetable -> {
            assertThat(timetable.getTotalCredits()).isEqualTo(18);
            assertThat(timetable.getCreditsByType().get("전공")).isEqualTo(9);
            assertThat(timetable.getCreditsByType().get("이중전공")).isEqualTo(6);
            timetable.getScheduledCourses().forEach(course -> {
                course.getActualClassTimes().forEach(slot -> {
                    assertThat(slot.getDay()).isNotEqualTo("Wed");
                    assertThat(slot.getEndPeriod()).isLessThanOrEqualTo(7);
                });
            });
        });
    }

    // =====================================================================================
    // >> 신규 추가: 시나리오 4
    // =====================================================================================
    @Test
    @DisplayName("11. [시나리오 4] 컴공/GBT 부전공 3학년, 월공강 & 21학점 & 복잡한 시간 선호")
    void should_recommend_timetables_for_cs_major_with_21_credits_and_complex_time_prefs() {
        // given: 1. 사용자 정보 및 선호도 설정
        User csUser = User.builder()
                .id(testUserId)
                .studentId("20220002")
                .name("컴공GBT테스트유저")
                .grade(3)
                .major("컴퓨터공학부")
                .doubleMajorType(DoubleMajorType.MINOR) // 부전공
                .doubleMajor("Global Business & Technology전공")
                .college(College.공과대학)
                .build();

        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setMandatoryCourses(List.of("V44301601", "P05307101")); // 컴퓨터구조, 인적자원관리

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Tue", List.of(3,4,5,6,7,8,9)),
                new TimeSlotDto("Wed", List.of(6,7,8,9)),
                new TimeSlotDto("Thu", List.of(1,2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Fri", List.of(3,4,5,6,7,8,9))
        ));

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(21);
        creditSettings.setMaxTotalCredits(21);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 9),      // 전공 9학점 (필수/재수강 6 + 선택 3)
                "이중전공", new CreditRangeDto(9, 9), // 이중전공 6학점 (필수 3 + 선택 3)
                "교양", new CreditRangeDto(3, 3)      // 교양 3학점
        ));
        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        // 2. Mock 서비스 설정
        given(userService.getUserDetails(testUserId)).willReturn(csUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        List<DetailedCourseInfo> realisticCourseList = createRealisticExtremeCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(realisticCourseList);
        realisticCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then
        assertThat(recommendations).isNotEmpty();
        recommendations.forEach(timetable -> {
            assertThat(timetable.getTotalCredits()).isEqualTo(21);
            assertThat(timetable.getCreditsByType().get("전공")).isEqualTo(12);
            assertThat(timetable.getCreditsByType().get("부전공")).isEqualTo(6);
            timetable.getScheduledCourses().forEach(course -> {
                course.getActualClassTimes().forEach(slot -> {
                    assertThat(slot.getDay()).isNotEqualTo("Mon");
                    switch (slot.getDay()) {
                        case "Tue" -> assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(3);
                        case "Wed" -> assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(6);
                        case "Thu" -> assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(1);
                        case "Fri" -> assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(3);
                    }
                });
            });
        });
    }

    // =====================================================================================
    // 테스트 데이터 생성 헬퍼 메소드
    // =====================================================================================
    private List<DetailedCourseInfo> createSimpleCourseList() {
        return List.of(
                createCourse("V41010101", "고급파이썬프로그래밍", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("V41010103", "고급파이썬프로그래밍(다른분반)", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(1, 2, 3))),
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
                createCourse("D01205A02", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                createCourse("P05201101", "데이터구조", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7,8,9))),
                createCourse("Y13115302", "대학중국어1", null, "교양", "교양", 3, new TimeSlotDto("Wed", List.of(7, 8,9))),
                createCourse("P05412101", "비즈니스머신러닝", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("P05309101", "빅데이터분석", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(1, 2, 3)))
        );
    }

    // 실제 데이터 기반의 극한 조건 테스트용 강의 데이터 생성 헬퍼
    private List<DetailedCourseInfo> createRealisticExtremeCourseList() {
        return List.of(
                // --- 기수강 과목 ---
                createCourse("V41002201", "컴퓨터수학", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                createCourse("D01205A01", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))),
                createCourse("V41010101", "고급파이썬프로그래밍", "AI융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("M01201101", "통계모델링", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4, 5, 6))),

                // --- 필수/재수강 과목 (반드시 포함되어야 함) ---
                createCourse("M01207101", "머신러닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                createCourse("P05406201", "졸업프로젝트실습(캡스톤디자인)", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(10, 11, 12))),
                createCourse("M01301101", "비정형데이터마이닝", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),

                // --- 조합 가능한 선택 과목들 (시간 제약 만족) ---
                // [전공: AI데이터융합전공, AI융합전공(Software&AI)]
                createCourse("M01202101", "자료구조와알고리즘", "AI데이터융합전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("V41008201", "알고리즘", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("V41020101", "인공지능", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("T07403201", "종합설계", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(7, 8, 9))),

                // [이중전공: Global Business & Technology전공]
                createCourse("P05201101", "데이터구조", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7, 8, 9))),
                createCourse("D10405701", "마케팅관리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(7, 8, 9))),
                createCourse("P05304201", "데이터베이스", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("P05307101", "인적자원관리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(4,5,6))),

                // [교양]
                createCourse("Y13115302", "대학중국어1", null, "교양", "교양", 3, new TimeSlotDto("Wed", List.of(7, 8, 9))),
                createCourse("U76269201", "광고.PR의이해", null, "교양", "교양", 2, new TimeSlotDto("Thu", List.of(3, 4))),
                createCourse("Y51215301", "도시의미학과문화", null, "교양", "교양", 2, new TimeSlotDto("Fri", List.of(5, 6))),

                // [전공: 스페인어통번역학과]
                createCourse("N05309101", "스페인어문학번역", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Tue", List.of(7, 8))),
                createCourse("A05301104", "고급스페인어회화(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(3, 4))),
                createCourse("A05303202", "고급스페인어작문(1)", "스페인어통번역학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(3, 4))),

                // [전공: 러시아학과]
                createCourse("A04223201", "러시아어말하기심화(1)", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(1, 2))),
                createCourse("A04359201", "러시아어문장론", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(3, 4))),
                createCourse("A04321201", "러시아어통역연습", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Thu", List.of(7, 8))),
                createCourse("A07377201", "러시아역사(1)", "러시아학과", "전공", "전공_후보", 2, new TimeSlotDto("Mon", List.of(1, 2))),

                // [전공: 컴퓨터공학전공]
                createCourse("V44301601", "컴퓨터구조", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Tue", List.of(5,6))), // 목7시 수업도 있지만, 화요일만 선택 가능하게 단순화
                createCourse("T05479201", "설계패턴", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Thu", List.of(2,3,4))), // 시간 임의 조정
                createCourse("T05402201", "데이터베이스설계", "컴퓨터공학전공", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(6,7,8))),

                // --- 제약조건에 걸리는 '함정' 과목들 ---
                createCourse("P05309101", "빅데이터분석", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Fri", List.of(4, 5, 6))), // 금요일 수업
                createCourse("D01205A01", "회계원리", "Global Business & Technology전공", "전공", "전공_후보", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))), // 1교시 수업
                createCourse("V41007201", "데이터사이언스", "AI융합전공(Software&AI)", "전공", "전공_후보", 3, new TimeSlotDto("Wed", List.of(1, 2, 3))) // 수요일 오전 수업
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