package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.PreferencesRequest;
import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import com.cesco.scheduly.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCourseSelectionRepository userCourseSelectionRepository; // 자식 테이블 리포지토리 추가

    @Autowired
    private UserPreferenceRepository userPreferenceRepository; // 자식 테이블 리포지토리 추가

    private User testUser;

    @BeforeEach
    void setUp() {
        // ★★★ 핵심 수정 부분: 생성의 역순으로 데이터를 삭제하여 제약 조건 위반 방지 ★★★
        userCourseSelectionRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트에 필요한 사용자 데이터 생성
        testUser = User.builder()
                .studentId("20250001")
                .name("테스트유저")
                .passwordHash("hashed_password")
                .college(College.공과대학)
                .major("컴퓨터공학전공")
                .doubleMajor("스페인어통번역학과")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .grade(2)
                .semester(1)
                .build();
        userRepository.saveAndFlush(testUser); // saveAndFlush로 즉시 DB에 반영

        // signup 시 자동으로 생성되는 엔티티들을 테스트에서도 명시적으로 생성
        UserPreferenceEntity preference = UserPreferenceEntity.builder().user(testUser).build();
        userPreferenceRepository.saveAndFlush(preference);
    }

    @Test
    @WithMockUser
    @DisplayName("이중전공 사용자가 부전공 학점 목표 설정 시 400 에러 응답 테스트")
    void should_return_bad_request_when_double_major_user_sets_minor_credit_goal() throws Exception {
        // given: 2. '부전공' 학점 목표가 포함된, 규칙에 맞지 않는 요청(Request) 데이터를 준비합니다.
        Long userId = testUser.getId();
        CreditSettingsRequest requestDto = new CreditSettingsRequest();
        requestDto.setCreditGoalsPerType(Map.of(
                "이중전공", new CreditRangeDto(9, 12),
                "부전공", new CreditRangeDto(3, 3) // <--- 이 부분이 유효성 검사에 걸리는 부분입니다.
        ));
        requestDto.setMinTotalCredits(12);
        requestDto.setMaxTotalCredits(15);

        String requestBody = objectMapper.writeValueAsString(requestDto);

        // when & then: 학점 목표 저장 API(/users/{userId}/timetable/preferences/settings)를 호출합니다.
        // 그 결과로 HTTP 400 상태 코드와 정확한 에러 메시지를 반환하는지 검증합니다.
        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print()) // 요청/응답 전체 내용을 콘솔에 출력하여 확인
                .andExpect(status().isBadRequest()) // 400 Bad Request 상태 코드를 기대
                .andExpect(jsonPath("$.message").value("'이중전공' 사용자는 '부전공' 또는 '전공심화' 학점 목표를 설정할 수 없습니다.")); // 정확한 에러 메시지를 기대
    }

    @Test
    @WithMockUser
    @DisplayName("시간 선호도 저장 기능 단위 테스트")
    void should_save_time_preferences_successfully() throws Exception {
        // given: "월요일 오전(1-4교시)"을 선호한다는 요청 데이터 준비
        Long userId = testUser.getId();
        TimePreferenceRequest requestDto = new TimePreferenceRequest();
        requestDto.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(1, 2, 3, 4))
        ));
        String requestBody = objectMapper.writeValueAsString(requestDto);

        // when: 시간 선호도 저장 API를 호출
        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()) // 1. API 호출이 성공했는지 확인 (200 OK)
                .andExpect(jsonPath("$.message").value("시간 선호도 저장 성공"));

        // then: DB에 데이터가 정확하게 저장되었는지 직접 검증
        UserPreferenceEntity savedPreference = userPreferenceRepository.findByUser(testUser).orElseThrow();
        TimePreferenceRequest savedData = savedPreference.getTimePreferences();

        // 2. 저장된 데이터가 null이 아닌지 확인
        assertThat(savedData).isNotNull();
        // 3. 저장된 선호 시간대 목록의 크기가 1개인지 확인
        assertThat(savedData.getPreferredTimeSlots()).hasSize(1);
        // 4. 저장된 첫 번째 시간대의 요일이 "Mon"이 맞는지 확인
        assertThat(savedData.getPreferredTimeSlots().get(0).getDay()).isEqualTo("Mon");
        // 5. 저장된 첫 번째 시간대의 교시 목록이 [1, 2, 3, 4]가 맞는지 확인
        assertThat(savedData.getPreferredTimeSlots().get(0).getPeriods()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @WithMockUser
    @DisplayName("필수/재수강 과목 시간 중복 시 400 에러 응답 테스트")
    void should_return_bad_request_when_mandatory_courses_conflict() throws Exception {
        // given: 시간이 실제로 겹치는 두 과목을 필수 과목으로 저장
        Long userId = testUser.getId();
        // "머신러닝"(월 4,5,6)과 시간이 겹치는 "운영체제"(월 5,6,7)를 함께 필수로 지정
        List<String> conflictingCourses = List.of("M01207101", "M01206101");

        // PreferencesController의 API를 호출하여 데이터 저장
        mockMvc.perform(post("/preferences/required?userId=" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingCourses)))
                .andExpect(status().isOk());

        // when & then: 시간표 추천 API를 호출했을 때, 400 상태 코드와 특정 에러 메시지를 반환하는지 검증
        mockMvc.perform(get("/users/" + userId + "/timetable/recommendations"))
                .andExpect(status().isBadRequest()) // 400 Bad Request 상태 코드를 기대
                .andExpect(jsonPath("$.message").value("필수/재수강 과목 간 시간이 중복됩니다: 머신러닝(M01207101), 운영체제(M01206101)")); // 정확한 에러 메시지를 기대
    }

    // 기존 테스트 메소드 이름을 더 명확하게 변경하고, 로직을 수정합니다.
    @Test
    @WithMockUser
    @DisplayName("유효한 학점 목표 설정값이 DB에 성공적으로 저장되는지 테스트")
    void should_save_credit_settings_successfully() throws Exception {
        // given: 1. 유효한 학점 목표 데이터를 준비합니다.
        Long userId = testUser.getId();
        CreditSettingsRequest requestDto = new CreditSettingsRequest();

        // 시나리오: 총 15~18학점 | 전공 9~12학점 | 교양 6학점
        requestDto.setMinTotalCredits(15);
        requestDto.setMaxTotalCredits(18);
        requestDto.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 12),
                "교양", new CreditRangeDto(6, 6)
        ));
        String requestBody = objectMapper.writeValueAsString(requestDto);

        // when: 2. 학점 목표 저장 API를 호출합니다.
        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk()) // 3. API 응답이 성공(200 OK)했는지 먼저 확인합니다.
                .andExpect(jsonPath("$.message").value("학점 목표 및 강의 조합 설정 저장 성공"));

        // then: 4. DB에 데이터가 정확하게 저장되었는지 직접 검증합니다.
        UserPreferenceEntity savedPreference = userPreferenceRepository.findByUser(testUser).orElseThrow();
        CreditSettingsRequest savedData = savedPreference.getCreditSettings();

        // 저장된 데이터가 null이 아닌지 확인
        assertThat(savedData).isNotNull();
        // 저장된 전체 최소/최대 학점이 보낸 값과 일치하는지 확인
        assertThat(savedData.getMinTotalCredits()).isEqualTo(15);
        assertThat(savedData.getMaxTotalCredits()).isEqualTo(18);
        // 저장된 유형별 학점 목표가 보낸 값과 일치하는지 확인
        assertThat(savedData.getCreditGoalsPerType()).hasSize(2);
        assertThat(savedData.getCreditGoalsPerType().get("전공").getMin()).isEqualTo(9);
        assertThat(savedData.getCreditGoalsPerType().get("전공").getMax()).isEqualTo(12);
        assertThat(savedData.getCreditGoalsPerType().get("교양").getMin()).isEqualTo(6);
    }
    @Test
    @WithMockUser
    @DisplayName("유형별 최소학점의 합이 전체 최대학점보다 클 때, 400 에러를 반환하는지 테스트")
    void should_return_bad_request_when_sum_of_min_credits_exceeds_max_total() throws Exception {
        // given: 의도적으로 잘못된 학점 목표 데이터를 준비
        Long userId = testUser.getId();
        CreditSettingsRequest requestDto = new CreditSettingsRequest();

        // 전체 최대 학점은 18로 설정
        requestDto.setMaxTotalCredits(18);
        requestDto.setMinTotalCredits(15); // 전체 최소 학점

        // 유형별 최소 학점의 합(12+9=21)이 전체 최대 학점(18)을 초과하도록 설정
        requestDto.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(12, 15),
                "이중전공", new CreditRangeDto(9, 12)
        ));

        String requestBody = objectMapper.writeValueAsString(requestDto);

        // when & then: 학점 목표 저장 API를 호출했을 때, 400 상태 코드와 특정 에러 메시지를 반환하는지 검증
        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest()) // 400 Bad Request 상태 코드를 기대
                .andExpect(jsonPath("$.message").value("유형별 최소 학점의 합(21)이 전체 최대 학점 목표(18)를 초과할 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("분할정복 검증: 복잡한 학점 목표와 특정 요일 제외 후 시간표 추천 테스트")
    void recommendsTimetable_WithComplexFilters_AndDayOffPreference() throws Exception {
        // given: 1. 테스트 사용자 생성 (컴공/TESOL영어학 이중전공)
        User complexUser = User.builder()
                .studentId("20210815")
                .name("복합조건테스터")
                .passwordHash("password")
                .college(College.공과대학)
                .major("컴퓨터공학전공")
                .doubleMajor("TESOL영어학전공") // 이중전공 설정
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .grade(3)
                .semester(1)
                .build();
        userRepository.save(complexUser);
        Long userId = complexUser.getId();

        UserPreferenceEntity preference = UserPreferenceEntity.builder().user(complexUser).build();
        userPreferenceRepository.save(preference);
        UserCourseSelectionEntity selection = UserCourseSelectionEntity.builder().user(complexUser).build();
        userCourseSelectionRepository.save(selection);


        // given: 2. 수강 정보 설정
        PreferencesRequest coursePrefs = new PreferencesRequest();
        coursePrefs.setCompleted_lectures(List.of("M01202101")); // 기수강: 자료구조와알고리즘 (추천 제외 대상)
        coursePrefs.setRequired_lectures(List.of("V41009201"));  // 필수: 운영체제(3학점 전공) (반드시 포함)
        userService.saveUserCourseSelections(userId, coursePrefs);

        // given: 3. 시간 선호도 설정: "금요일은 공강일" 로 설정
        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(1,2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Tue", List.of(1,2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Wed", List.of(1,2,3,4,5,6,7,8,9)),
                new TimeSlotDto("Thu", List.of(1,2,3,4,5,6,7,8,9))
                // 금요일은 의도적으로 제외
        ));
        userService.saveTimePreferences(userId, timePrefs);

        // given: 4. 복잡한 학점 목표 설정
        CreditSettingsRequest creditPrefs = new CreditSettingsRequest();
        creditPrefs.setMinTotalCredits(16);
        creditPrefs.setMaxTotalCredits(19);
        creditPrefs.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(6, 6),       // 필수 3 + 선택 3 = 정확히 6학점
                "이중전공", new CreditRangeDto(6, 6), // 정확히 6학점
                "교양", new CreditRangeDto(4, 4)        // 정확히 4학점
        ));
        creditPrefs.setCourseTypeCombination(List.of("전공", "이중전공", "교양"));
        userService.saveCreditAndCombinationPreferences(userId, creditPrefs);


        // when: 시간표 추천 API 호출
        ResultActions resultActions = mockMvc.perform(
                get("/users/" + userId + "/timetable/recommendations")
        );


        // then: 모든 조건이 완벽하게 반영된 결과가 빠르게 반환되는지 검증
        resultActions.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables").isNotEmpty()) // 1. 추천 결과가 존재해야 함
                // 2. 전체 학점 조건 검증 (16~19학점)
                .andExpect(jsonPath("$.timetables[0].totalCredits", allOf(greaterThanOrEqualTo(16), lessThanOrEqualTo(19))))
                // 3. 유형별 학점 목표 정확성 검증
                .andExpect(jsonPath("$.timetables[0].creditsByType.전공", is(6)))
                .andExpect(jsonPath("$.timetables[0].creditsByType.이중전공", is(6)))
                .andExpect(jsonPath("$.timetables[0].creditsByType.교양", is(4)))
                // 4. 필수/기수강 과목 반영 여부 검증
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].courseCode", hasItem("V41009201")))     // 필수 과목 '운영체제' 포함
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].courseCode", not(hasItem("M01202101")))) // 기수강 과목 '자료구조' 미포함
                // 5. 시간 선호도(금요일 제외) 반영 여부 검증
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].scheduleSlots[*].day", everyItem(not(is("Fri")))));
    }


    @Test
    @WithMockUser
    @DisplayName("모든 필터링(필수,기수강,시간,학점) 적용 후 최종 시간표 추천 테스트")
    void should_recommend_timetable_that_satisfies_all_user_preferences() throws Exception {
        // given: 1. 테스트를 위한 기준 사용자 설정
        User testUser = User.builder()
                .studentId("20210303")
                .name("종합테스터")
                .passwordHash("password")
                .college(College.공과대학)
                .major("컴퓨터공학전공")
                .doubleMajor("스페인어통번역학과")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .grade(3)
                .semester(1)
                .build();
        userRepository.save(testUser);
        Long userId = testUser.getId();

        UserPreferenceEntity preference = UserPreferenceEntity.builder().user(testUser).build();
        userPreferenceRepository.save(preference);
        UserCourseSelectionEntity selection = UserCourseSelectionEntity.builder().user(testUser).build();
        userCourseSelectionRepository.save(selection);

        // given: 2. 사용자의 모든 선호도 및 수강 정보를 DTO에 담아 저장
        PreferencesRequest coursePrefs = new PreferencesRequest();
        coursePrefs.setCompleted_lectures(List.of("T05306201")); // 기수강: 논리회로 수12, 금3
        coursePrefs.setRequired_lectures(List.of("F02314301"));  // 필수: 전자기학 월34, 수5
        coursePrefs.setRetake_lectures(List.of("T01209301"));   // 재수강: 전기회로 수6, 금78
        userService.saveUserCourseSelections(userId, coursePrefs);

        TimePreferenceRequest timePrefs = new TimePreferenceRequest();
        timePrefs.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Tue", List.of(3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Wed", List.of(3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Thu", List.of(3, 4, 5, 6, 7, 8, 9)),
                new TimeSlotDto("Fri", List.of(3, 4, 5, 6, 7, 8, 9))
        ));
        userService.saveTimePreferences(userId, timePrefs);

        CreditSettingsRequest creditPrefs = new CreditSettingsRequest();
        creditPrefs.setMinTotalCredits(15);
        creditPrefs.setMaxTotalCredits(18);
        creditPrefs.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(6, 6),
                "이중전공", new CreditRangeDto(6, 6),
                "교양", new CreditRangeDto(3, 3)
        ));
        creditPrefs.setCourseTypeCombination(List.of("전공", "이중전공", "교양"));
        userService.saveCreditAndCombinationPreferences(userId, creditPrefs);

        // when: 3. 시간표 추천 API를 호출
        ResultActions resultActions = mockMvc.perform(
                get("/users/" + userId + "/timetable/recommendations")
        );

        // then: 4. 모든 필터링 조건이 반영된 결과가 반환되었는지 검증
        resultActions.andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.timetables").isNotEmpty())
                .andExpect(jsonPath("$.timetables[0].totalCredits", allOf(greaterThanOrEqualTo(15), lessThanOrEqualTo(18))))
                .andExpect(jsonPath("$.timetables[0].creditsByType.전공", is(6)))
                .andExpect(jsonPath("$.timetables[0].creditsByType.이중전공", is(6)))
                .andExpect(jsonPath("$.timetables[0].creditsByType.교양", is(3)))
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].courseCode", hasItem("F02314301"))) // 필수
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].courseCode", hasItem("T01209301"))) // 재수강
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].courseCode", not(hasItem("T05306201")))) // 기수강 제외
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[*].scheduleSlots[*].periods[0]", everyItem(greaterThanOrEqualTo(3)))); // 시간 선호도
    }

}