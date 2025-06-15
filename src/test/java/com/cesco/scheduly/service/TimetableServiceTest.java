package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    @InjectMocks
    private TimetableService timetableService;

    @Mock
    private UserService userService;

    @Mock
    private CourseDataService courseDataService;

    private User testUser;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        // 1. 테스트에 사용할 가짜 사용자(User) 객체 생성
        testUser = User.builder()
                .id(testUserId)
                .studentId("20240001")
                .name("종합테스트유저")
                .grade(2)
                .major("AI데이터융합전공")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("Global Business & Technology전공")
                .college(College.AI융합대학)
                .build();
    }

    @Test
    @DisplayName("모든 필터링(필수/기수강/시간/학점) 적용 후 최종 시간표 추천 성공 테스트")
    void should_generate_valid_timetable_with_all_constraints() {
        // given: 1. 사용자의 모든 복잡한 설정을 정의합니다.

        // - 기수강, 필수, 재수강 과목 설정
        UserCourseSelectionEntity selections = new UserCourseSelectionEntity();
        selections.setTakenCourses(List.of("V41010101", "V41002201")); // 고파썬, 컴논개
        selections.setMandatoryCourses(List.of("M01201101"));     // 통계모델링, 수456, 필수
        selections.setRetakeCourses(List.of("M01301101"));        // 비정형데이터마이닝, 화456, 재수강

        // - 시간 및 학점 선호도 설정
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(4, 5, 6, 7)), // 월요일 오후
                new TimeSlotDto("Wed", List.of(4, 5, 6, 7, 8, 9)), // 수요일 오후
                new TimeSlotDto("Tue", List.of(4, 5, 6, 7, 8, 9)) // 수요일 오후
        ));

        CreditSettingsRequest creditSettings = new CreditSettingsRequest();
        creditSettings.setMinTotalCredits(15);
        creditSettings.setMaxTotalCredits(18);
        creditSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(6, 9),
                "이중전공", new CreditRangeDto(3, 6),
                "교양", new CreditRangeDto(3, 3)
        ));

        UserPreferenceEntity preferences = new UserPreferenceEntity();
        preferences.setTimePreferences(timePrefs);
        preferences.setCreditSettings(creditSettings);

        // 2. 가짜 서비스(Mock)가 어떤 데이터를 반환할지 미리 정의합니다.
        given(userService.getUserDetails(testUserId)).willReturn(testUser);
        given(userService.getUserCourseSelection(testUserId)).willReturn(selections);
        given(userService.getUserPreference(testUserId)).willReturn(preferences);

        // - 가짜 강의 데이터 목록 생성
        List<DetailedCourseInfo> mockCourseList = createMockCourseList();
        given(courseDataService.getDetailedCourses()).willReturn(mockCourseList);
        // getDetailedCourseByCode 호출 시, 목록에서 해당 코드를 찾아 반환하도록 설정
        mockCourseList.forEach(course ->
                given(courseDataService.getDetailedCourseByCode(course.getCourseCode())).willReturn(course)
        );

        // when: 3. 핵심 메소드인 시간표 추천 기능을 실행합니다.
        List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(testUserId);

        // then: 4. 모든 조건이 반영된 결과가 나왔는지 정밀하게 검증합니다.
        assertThat(recommendations).isNotEmpty(); // 최소 1개 이상의 시간표가 추천되어야 함

        RecommendedTimetableDto firstTimetable = recommendations.get(0);
        List<String> recommendedCourseCodes = firstTimetable.getScheduledCourses().stream()
                .map(ScheduledCourseDto::getCourseCode)
                .toList();

        // - 필수/재수강/기수강 과목 검증
        assertThat(recommendedCourseCodes).contains("M01201101"); // 필수과목 포함 확인
        assertThat(recommendedCourseCodes).contains("M01301101"); // 재수강과목 포함 확인
        assertThat(recommendedCourseCodes).doesNotContain("V41010101", "V41002201"); // 순수 기수강과목(컴과개론) 미포함 확인

        // - 학점 목표 검증
        Map<String, Integer> creditsByType = firstTimetable.getCreditsByType();
        assertThat(firstTimetable.getTotalCredits()).isBetween(15, 18); // 총 학점 범위 확인
        assertThat(creditsByType.get("전공")).isBetween(6, 9);
        assertThat(creditsByType.get("이중전공")).isBetween(3, 6);
        assertThat(creditsByType.get("교양")).isEqualTo(3);

        // - 시간 선호도 검증
        firstTimetable.getScheduledCourses().forEach(course -> {
            course.getActualClassTimes().forEach(slot -> {
                assertThat(slot.getDay()).isIn("Mon", "Tue", "Wed"); // 추천된 과목은 월요일 또는 수요일이어야 함
                assertThat(slot.getStartPeriod()).isGreaterThanOrEqualTo(4); // 모든 수업은 5교시 이후에 시작해야 함
            });
        });
    }

    // 테스트에 사용할 가짜 강의 데이터 생성 헬퍼 메소드
    private List<DetailedCourseInfo> createMockCourseList() {
        return List.of(
                // 기수강 과목 (추천 제외 대상)
                createCourse("V41010101", "고급파이썬프로그래밍", "AI융합전공", "전공", 3, new TimeSlotDto("Thu", List.of(4, 5, 6))),
                createCourse("V41002201", "컴퓨터수학", "AI융합전공", "전공", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                // 필수 과목 (반드시 포함)
                createCourse("M01201101", "통계모델링", "AI데이터융합전공", "전공", 3, new TimeSlotDto("Wed", List.of(4, 5, 6))),
                // 재수강 과목 (반드시 포함)
                createCourse("M01301101", "비정형데이터마이닝", "AI데이터융합전공", "전공", 3, new TimeSlotDto("Tue", List.of(4, 5, 6))),
                // 이중전공 선택과목 (선택 가능)
                createCourse("D01205A02", "회계원리", "Global Business & Technology전공", "전공", 3, new TimeSlotDto("Mon", List.of(4, 5, 6))),
                // 교양 선택과목 (선택 가능)
                createCourse("Y13115302", "대학중국어1", null, "교양", 3, new TimeSlotDto("Wed", List.of(7, 8,9))),
                // 시간 선호도에 맞지 않는 과목 (추천 제외 대상)
                createCourse("P05412101", "비즈니스머신러닝", "Global Business & Technology전공", "전공", 3, new TimeSlotDto("Mon", List.of(1, 2, 3))), // 오전 수업
                // 요일이 맞지 않는 과목 (추천 제외 대상)
                createCourse("P05309101", "빅데이터분석", "Global Business & Technology전공", "전공", 3, new TimeSlotDto("Fri", List.of(1, 2, 3))) // 금요일 수업
        );
    }

    private DetailedCourseInfo createCourse(String code, String name, String specificMajor, String deptOriginal, int credits, TimeSlotDto time) {
        DetailedCourseInfo course = new DetailedCourseInfo();
        course.setCourseCode(code);
        course.setCourseName(name);
        course.setSpecificMajor(specificMajor);
        course.setDepartmentOriginal(deptOriginal);
        course.setCredits(credits);
        course.setGroupId(code.substring(0, 4));
        course.setScheduleSlots(List.of(time));
        return course;
    }
}