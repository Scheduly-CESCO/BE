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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient; // lenient() 사용
import static org.mockito.Mockito.when;
// import static org.mockito.ArgumentMatchers.any; // 필요시 사용

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {
    private static final Logger logger_test = LoggerFactory.getLogger(TimetableServiceTest.class);

    @Mock
    private UserService userService;

    @Mock
    private CourseDataService courseDataService;

    @InjectMocks
    private TimetableService timetableService;

    private UserEntity sampleUser;
    private UserCourseSelectionEntity sampleUserSelections;
    private UserPreferenceEntity sampleUserPreferences;
    private List<DetailedCourseInfo> allSampleCoursesSetup;

    // 학수번호 정의
    private final String gbtMajorCode1 = "D01205A01"; // 회계원리	3	3	김문현	월 1 2 3 (4403)
    private final String gbtMajorCode2 = "P05208101"; // 객체지향프로그래밍	3	3	한영진	수 4 5 6 (4212)
    private final String gbtMajorCode3 = "P05201101"; // 데이터구조	3	3	이근섭	목 7 8 9 (4207)
    private final String gbtMajorCode4Conflict = "P05209101"; // AI를위한기초알고리즘	3	3	배주호	수 4 5 6 (4207)
    private final String aiDoubleMajorCode1 = "V41009201"; // 운영체제	3	3	임승호	목 1 2 3 (5309)
    private final String geCourseCode1 = "U72247301";    // 에스페란토어의이해	2	2	신현규	목 3 4 (2506)
    private final String geCourseCode2 = "U76002101";    // 위대한개츠비로본문학비평이론	2	2	김윤정	수 7 8 (2410)
    private final String geCourseCode3 = "U51416101";    // 생활과화학	2	2	김민선	목 5 6 (2409)
    private final String selfSelectionCandidateCode = "O02210101"; // 현대일본어표현활용	2	2	박민영	수 1 2 (1506)
    private final String restrictedSeminarCode = "Y11113901"; // 신입생세미나(GBT)	1	1	김병초	월 9 (4403)
    private final String takenCourseCode = "Y12101601"; // 미네르바인문(1)읽기와 쓰기	3	3	나영남	월 1 2 3 (2313)

    @BeforeEach
    void setUp() {
        sampleUser = UserEntity.builder()
                .userId("userTest01")
                .grade("3")
                .major("Global Business & Technology")
                .doubleMajor("AI융합전공")
                .build();

        sampleUserSelections = UserCourseSelectionEntity.builder()
                .user(sampleUser)
                .mandatoryCourses(new ArrayList<>())
                .takenCourses(List.of(takenCourseCode))
                .retakeCourses(new ArrayList<>())
                .build();

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(10); // 테스트 시나리오에 맞게 조정될 수 있음
        creditSettings.setMaxTotalCredits(18);
        Map<String, CreditRangeDto> goals = new HashMap<>();
        goals.put("전공", new CreditRangeDto(3, 9)); // 기본값, 테스트에서 오버라이드
        goals.put("이중전공", new CreditRangeDto(0, 6));
        goals.put("교양", new CreditRangeDto(1, 6));
        goals.put("자선", new CreditRangeDto(0, 3));
        creditSettings.setCreditGoalsPerType(goals);

        sampleUserPreferences = UserPreferenceEntity.builder()
                .user(sampleUser)
                .timePreferences(timePrefs)
                .creditSettings(creditSettings)
                .build();

        // DetailedCourseInfo 샘플 (groupId, specificMajor, generalizedType, isRestrictedCourse 포함)
        // CourseDataService에서 로드 시 generalizedType, isRestrictedCourse, groupId가 채워진다고 가정
        allSampleCoursesSetup = new ArrayList<>(List.of(
                new DetailedCourseInfo(gbtMajorCode1, "회계원리", "전공", "Global Business & Technology", "D01205A", "전공", 3, 3, "2", "김GBT", "경101", "GBT주전공", List.of(new TimeSlotDto("Mon", List.of(1,2,3))), false),
                new DetailedCourseInfo(gbtMajorCode2, "객체지향프로그래밍", "전공", "Global Business & Technology", "P052081", "전공", 3, 3, "3", "이GBT", "경102", "GBT실무와 동일과목군", List.of(new TimeSlotDto("Tue", List.of(1,2,3))), false),
                new DetailedCourseInfo(gbtMajorCode3, "데이터구조", "전공", "Global Business & Technology", "P052011", "전공", 3, 3, "3", "박GBT", "경103", "GBT주전공", List.of(new TimeSlotDto("Wed", List.of(1,2,3))), false),
                new DetailedCourseInfo(gbtMajorCode4Conflict, "AI를위한기초알고리즘", "전공", "Global Business & Technology", "P052091", "전공", 3, 3, "3", "조GBT", "경104", "GBT주전공", List.of(new TimeSlotDto("Mon", List.of(2,3,4))), false),
                new DetailedCourseInfo(aiDoubleMajorCode1, "운영체제", "전공", "AI융합전공", "V410092", "이중전공", 3, 3, "2", "최AI", "공101", "AI융합개설", List.of(new TimeSlotDto("Mon", List.of(4,5,6))), false),
                new DetailedCourseInfo(geCourseCode1, "에스페란토어의이해", "교양", null, "U722473", "교양", 2, 2, "1", "박교양", "교101", "", List.of(new TimeSlotDto("Mon", List.of(7,8))), false),
                new DetailedCourseInfo(geCourseCode2, "위대한개츠비로본문학비평이론", "교양", null, "U760021", "교양", 1, 1, "2", "이교양", "교102", "", List.of(new TimeSlotDto("Tue", List.of(7))), false),
                new DetailedCourseInfo(geCourseCode3, "생활과화학", "교양", null, "U514161", "교양", 3, 3, "1", "김교양", "교103", "", List.of(new TimeSlotDto("Fri", List.of(1,2,3))), false),
                new DetailedCourseInfo(selfSelectionCandidateCode, "현대일본어표현활용", "전공", "일본어통번역학과", "O022101", "자선", 3, 3, "2", "강경제", "상101", "타과생수강가능", List.of(new TimeSlotDto("Thu", List.of(1,2,3))), false),
                new DetailedCourseInfo(restrictedSeminarCode, "신입생세미나", "신입생세미나", "자유전공학부", "Y111139", "신입생세미나", 1, 1, "1", "학부장", "셈A", "자유전공필참", List.of(new TimeSlotDto("Fri", List.of(4))), true),
                new DetailedCourseInfo(takenCourseCode, "미네르바인문(1)읽기와 쓰기", "교양", null, "Y121016", "교양", 3, 3, "1", "이교수", "교202", "", List.of(new TimeSlotDto("Mon", List.of(8,9))), false)
        ));

        when(courseDataService.getDetailedCourses()).thenReturn(new ArrayList<>(allSampleCoursesSetup));
        // getDetailedCourseByCode는 필요한 테스트에서만 설정 (UnnecessaryStubbing 방지)
    }

    @Test
    @DisplayName("기본 추천: 주전공(GBT 6학점), 이중전공(AI 3학점), 교양(3학점) 목표 만족")
    void generateRecommendations_SatisfySetGoals() {
        // Arrange
        CreditSettingsRequest settings = new CreditSettingsRequest();
        settings.setMinTotalCredits(12);
        settings.setMaxTotalCredits(12); // 정확히 12학점 목표
        Map<String, CreditRangeDto> goals = new HashMap<>();
        goals.put("전공", new CreditRangeDto(6, 6));     // GBT 실무(3) + GBT 마케팅(3) = 6
        goals.put("이중전공", new CreditRangeDto(3, 3)); // AI 프로그래밍(3) = 3
        goals.put("교양", new CreditRangeDto(3, 3));     // 교양선택C(3) = 3
        settings.setCreditGoalsPerType(goals);
        sampleUserPreferences.setCreditSettings(settings);

        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections);
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);
        // getDetailedCourseByCode는 TimetableService.prepareCandidateCourses 내부에서 호출될 수 있음
        allSampleCoursesSetup.forEach(course ->
                lenient().when(courseDataService.getDetailedCourseByCode(course.getCourseCode())).thenReturn(course)
        );

        // Act
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(sampleUser.getUserId());

        // Assert
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "주/이중/교양 학점 목표를 만족하는 추천이 1개 이상 생성되어야 합니다. 실제 추천 수: " + recommendations.size());

        if (!recommendations.isEmpty()) {
            RecommendedTimetableDto firstRec = recommendations.get(0);
            logger_test.info("추천 결과 (주/이중/교양): {}", firstRec.getScheduledCourses().stream().map(ScheduledCourseDto::getCourseName).collect(Collectors.toList()));
            logger_test.info("학점: 총 {}, 유형별: {}", firstRec.getTotalCredits(), firstRec.getCreditsByType());

            assertEquals(12, firstRec.getTotalCredits(), "총 학점이 일치해야 합니다.");
            assertEquals(6, firstRec.getCreditsByType().getOrDefault("전공", 0));
            assertEquals(3, firstRec.getCreditsByType().getOrDefault("이중전공", 0));
            assertEquals(3, firstRec.getCreditsByType().getOrDefault("교양", 0));
        }
    }

    @Test
    @DisplayName("필수 과목 간 시간 중복 시 예외 발생 테스트")
    void generateRecommendations_MandatoryCoursesConflict() {
        // Arrange
        DetailedCourseInfo mandatory1 = allSampleCoursesSetup.stream().filter(c->c.getCourseCode().equals(gbtMajorCode1)).findFirst().get(); // GBT 실무 (Mon 1,2,3)
        DetailedCourseInfo mandatory2Conflict = allSampleCoursesSetup.stream().filter(c->c.getCourseCode().equals(gbtMajorCode4Conflict)).findFirst().get(); // GBT 프로젝트 (Mon 2,3,4) - 시간 중복

        sampleUserSelections.setMandatoryCourses(List.of(gbtMajorCode1, gbtMajorCode4Conflict));

        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections);
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);

        // 테스트에 필요한 강의 목록만 반환하도록 모킹 (prepareCandidateCourses가 이 리스트를 사용)
        List<DetailedCourseInfo> conflictTestCourses = List.of(mandatory1, mandatory2Conflict);
        when(courseDataService.getDetailedCourses()).thenReturn(conflictTestCourses);
        // getAndValidateMandatoryCourses 내부에서 candidatePool (여기서는 conflictTestCourses)을 사용하므로,
        // getDetailedCourseByCode 모킹은 필요 없음 (candidatePool에서 직접 가져옴)

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            timetableService.generateRecommendations(sampleUser.getUserId());
        });

        assertTrue(exception.getMessage().contains("필수 또는 재수강 과목 간에 시간이 중복되거나, 동일 과목이 중복 선택되었습니다. 선택을 조정한 후 다시 시도해주세요."));
    }

    // ... (generateRecommendations_WithSelfSelectionGoal,
    //      generateRecommendations_NoDuplicateGroupIdCourses_WhenOneIsMandatory,
    //      generateRecommendations_RestrictedCourse_TypeSelected_NotMandatory,
    //      generateRecommendations_RestrictedCourseIsMandatory_ShouldBeIncluded 테스트는 이전 답변의 수정된 형태를 유지하되,
    //      setUp에서 설정된 sampleAllCourses를 기반으로 데이터를 설정하고,
    //      필요한 getDetailedCourseByCode 모킹은 lenient()로 처리하거나 각 테스트 내에서 명시적으로 설정합니다.)

    // 예시: 자선 과목 테스트 (필요한 모킹만 설정)
    @Test
    @DisplayName("자선 과목 추천 테스트")
    void generateRecommendations_WithSelfSelectionGoal_ShouldIncludeSelfSelection() {
        // Arrange
        CreditSettingsRequest settings = new CreditSettingsRequest();
        settings.setMinTotalCredits(3); settings.setMaxTotalCredits(3);
        Map<String, CreditRangeDto> goals = new HashMap<>();
        goals.put("자선", new CreditRangeDto(3,3));
        settings.setCreditGoalsPerType(goals);
        sampleUserPreferences.setCreditSettings(settings);
        sampleUserSelections.setMandatoryCourses(Collections.emptyList());

        when(userService.getUserDetails(anyString())).thenReturn(sampleUser);
        when(userService.getUserCourseSelection(anyString())).thenReturn(sampleUserSelections);
        when(userService.getUserPreference(anyString())).thenReturn(sampleUserPreferences);
        // getDetailedCourses는 setUp에서 모킹됨

        // Act
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(sampleUser.getUserId());

        // Assert
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "자선 과목을 포함하는 추천이 생성되어야 합니다.");
        if (!recommendations.isEmpty()) {
            RecommendedTimetableDto firstRec = recommendations.get(0);
            logger_test.info("추천 결과 (자선 포함): {}", firstRec.getScheduledCourses().stream().map(sc -> sc.getCourseName() + "(" + sc.getDepartment() + ")").collect(Collectors.toList()));
            assertEquals(3, firstRec.getCreditsByType().getOrDefault("자선", 0), "자선 과목 학점이 3학점이어야 합니다.");
            boolean selfSelectCoursePresent = firstRec.getScheduledCourses().stream()
                    .anyMatch(c -> selfSelectionCandidateCode.equals(c.getCourseCode()) && "자선".equals(c.getDepartment()));
            assertTrue(selfSelectCoursePresent, selfSelectionCandidateCode + "가 '자선'으로 추천되어야 합니다.");
        }
    }
}