package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
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
    @DisplayName("선호 시간대 저장 및 해당 시간대 강의만 추천되는지 테스트")
    void should_recommend_courses_only_within_preferred_time_slots() throws Exception {
        // given: 1. "월요일 오전(1-4교시)"만 선호한다고 설정하고 API를 통해 저장
        Long userId = testUser.getId();
        TimePreferenceRequest requestDto = new TimePreferenceRequest();
        requestDto.setPreferredTimeSlots(List.of(
                new TimeSlotDto("Mon", List.of(1, 2, 3, 4))
        ));
        String requestBody = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // given: 2. 해당 사용자에게 필수 과목을 아무것도 설정하지 않음 (선택 과목만 추천받는 상황)

        // when: 시간표 추천 API를 호출
        ResultActions resultActions = mockMvc.perform(
                get("/users/" + userId + "/timetable/recommendations")
        );

        // then: 3. 추천 결과에 월요일 오전에 진행되는 과목만 포함되어 있는지 검증
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables").isNotEmpty()) // 최소 하나 이상의 시간표가 추천되었는지 확인
                // 첫 번째 추천 시간표의 첫 번째 과목의 요일이 "Mon"인지 확인
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[0].actualClassTimes[0].요일").value("Mon"))
                // 첫 번째 추천 시간표의 첫 번째 과목의 시작 교시가 1 이상인지 확인
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[0].actualClassTimes[0].start_period").value(greaterThanOrEqualTo(1)))
                // 첫 번째 추천 시간표의 첫 번째 과목의 종료 교시가 4 이하인지 확인
                .andExpect(jsonPath("$.timetables[0].scheduledCourses[0].actualClassTimes[0].end_period").value(lessThanOrEqualTo(4)));
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

    @Test
    @WithMockUser
    @DisplayName("학점 목표 설정 후, 목표에 맞는 학점으로 시간표가 추천되는지 테스트")
    void should_recommend_timetable_that_meets_credit_goals() throws Exception {
        // given: 1. "전공 6, 이중전공 6, 교양 4학점"을 목표로 설정하고 저장
        Long userId = testUser.getId();
        CreditSettingsRequest requestDto = new CreditSettingsRequest();

        // 사용자의 학점 목표 설정
        requestDto.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(6, 6),
                "이중전공", new CreditRangeDto(6, 6), // '부전공'은 로직상 '이중전공'으로 처리될 수 있음
                "교양", new CreditRangeDto(4, 4)
        ));
        // 사용자가 학점 목표를 설정한 유형들
        requestDto.setCourseTypeCombination(List.of("전공", "이중전공", "교양"));
        requestDto.setMinTotalCredits(16);
        requestDto.setMaxTotalCredits(16);

        String requestBody = objectMapper.writeValueAsString(requestDto);

        // 학점 목표 저장 API 호출
        mockMvc.perform(put("/users/" + userId + "/timetable/preferences/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // when: 2. 시간표 추천 API를 호출
        ResultActions resultActions = mockMvc.perform(
                get("/users/" + userId + "/timetable/recommendations")
        );

        // then: 3. 추천 결과의 학점이 설정한 목표와 일치하는지 검증
        resultActions.andExpect(status().isOk())
                .andDo(print()) // 응답 JSON을 콘솔에 자세히 출력
                .andExpect(jsonPath("$.timetables").isNotEmpty())
                // 첫 번째 추천 시간표의 총 학점이 16학점인지 확인
                .andExpect(jsonPath("$.timetables[0].totalCredits").value(16))
                // 유형별 학점이 목표와 일치하는지 확인
                .andExpect(jsonPath("$.timetables[0].creditsByType.전공").value(6))
                .andExpect(jsonPath("$.timetables[0].creditsByType.이중전공").value(6))
                .andExpect(jsonPath("$.timetables[0].creditsByType.교양").value(4));
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
}