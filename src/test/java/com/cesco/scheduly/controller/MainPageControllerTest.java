package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.timetable.RecommendedTimetableDto;
import com.cesco.scheduly.dto.timetable.ScheduledCourseDto;
import com.cesco.scheduly.dto.timetable.TimeSlotDto;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MainPageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 테스트 독립성을 위해 매번 관련 데이터 삭제
        userPreferenceRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성
        testUser = User.builder()
                .studentId("20257777")
                .name("메인페이지테스터")
                .passwordHash("hashed_password")
                .college(College.AI융합대학)
                .major("AI데이터융합학부")
                .doubleMajorType(DoubleMajorType.DOUBLE_MAJOR)
                .doubleMajor("컴퓨터공학전공")
                .grade(3).semester(1).build();
        userRepository.save(testUser);
    }

    @Test
    @WithMockUser
    @DisplayName("메인페이지 상단 사용자 정보 조회 성공 테스트")
    void should_get_main_page_user_info() throws Exception {
        // given
        Long userId = testUser.getId();

        // when & then
        mockMvc.perform(get("/users/" + userId + "/main/info"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.name").value("메인페이지테스터"))
                .andExpect(jsonPath("$.studentId").value("20257777"))
                .andExpect(jsonPath("$.college").value("AI융합대학"))
                .andExpect(jsonPath("$.major").value("AI데이터융합학부"))
                .andExpect(jsonPath("$.doubleMajor").value("컴퓨터공학전공"));
    }

    @Test
    @WithMockUser
    @DisplayName("저장된 시간표가 있을 경우, 메인페이지에서 성공적으로 조회하는지 테스트")
    void should_load_saved_timetable_on_main_page() throws Exception {
        // given: 1. 사용자의 DB에 저장된 시간표가 있는 상황을 만듭니다.
        Long userId = testUser.getId();
        ScheduledCourseDto course = new ScheduledCourseDto("M01201101", "통계모델링", "전공", 3, "장혜진", "2515", "",
                List.of(new TimeSlotDto("Wed", List.of(4, 5, 6))));
        RecommendedTimetableDto savedTimetable = new RecommendedTimetableDto(1, List.of(course), Map.of("전공", 3), 3);
        String timetableJson = objectMapper.writeValueAsString(savedTimetable);

        UserPreferenceEntity preference = UserPreferenceEntity.builder()
                .user(testUser)
                .savedTimetableJson(timetableJson)
                .build();
        userPreferenceRepository.save(preference);

        // when: 2. 메인페이지 콘텐츠 조회 API를 호출합니다.
        mockMvc.perform(get("/users/" + userId + "/main/content"))
                .andExpect(status().isOk())
                .andDo(print())
                // then: 3. API 응답이 저장했던 시간표와 일치하는지 검증합니다.
                .andExpect(jsonPath("$.hasTimetable").value(true))
                .andExpect(jsonPath("$.message").value("저장된 시간표입니다."))
                .andExpect(jsonPath("$.timetable.totalCredits").value(3))
                .andExpect(jsonPath("$.timetable.scheduledCourses[0].courseCode").value("M01201101"));
    }

    @Test
    @WithMockUser
    @DisplayName("저장된 시간표가 없을 때, '시간표 없음' 메시지를 반환하는지 테스트")
    void should_return_no_timetable_message_when_nothing_is_saved() throws Exception {
        // given: 아무것도 저장하지 않은 깨끗한 사용자 상태
        Long userId = testUser.getId();
        UserPreferenceEntity preference = UserPreferenceEntity.builder().user(testUser).build();
        userPreferenceRepository.save(preference); // savedTimetableJson이 null인 상태

        // when: 메인페이지 콘텐츠 조회 API를 호출
        mockMvc.perform(get("/users/" + userId + "/main/content"))
                .andExpect(status().isOk())
                .andDo(print())
                // then: 시간표가 없다는 메시지를 반환하는지 검증
                .andExpect(jsonPath("$.hasTimetable").value(false))
                .andExpect(jsonPath("$.timetable").isEmpty())
                .andExpect(jsonPath("$.message").value("아직 생성된 시간표가 없어요! 시간표를 생성하러 가볼까요?"));
    }
}