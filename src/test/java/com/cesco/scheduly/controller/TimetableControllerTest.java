package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.service.TimetableService;
import com.cesco.scheduly.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = TimetableController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}) // Security 자동 설정 제외
public class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TimetableService timetableService;

    @MockBean
    private UserService userService;

    private String testUserId;
    private TimePreferenceRequest timePreferenceRequest;
    private CreditSettingsRequest creditSettingsRequest;

    @BeforeEach
    void setUp() {
        testUserId = "userTest123";

        timePreferenceRequest = new TimePreferenceRequest();
        timePreferenceRequest.setAvoidDays(List.of("Fri"));
        // timePreferenceRequest.setPreferNoClassDays(true); // 필요시 설정

        creditSettingsRequest = new CreditSettingsRequest();
        creditSettingsRequest.setMinTotalCredits(15);
        creditSettingsRequest.setMaxTotalCredits(18);
        Map<String, CreditRangeDto> goals = new HashMap<>();
        goals.put("전공", new CreditRangeDto(9, 12));
        goals.put("교양", new CreditRangeDto(3, 6));
        creditSettingsRequest.setCreditGoalsPerType(goals);
        // creditSettingsRequest.setCourseTypeCombination(List.of("전공", "교양")); // 이 필드는 이제 선택적
    }

    @Test
    @DisplayName("시간 선호도 업데이트 API 테스트 - 성공")
    @WithMockUser // 인증된 사용자로 가정 (CSRF는 SecurityConfig에서 disable 했거나, 여기서 .with(csrf()) 필요)
    void updateUserTimePreferences_Success() throws Exception {
        // Arrange
        // userService.saveTimePreferences는 void를 반환하므로 doNothing() 사용
        doNothing().when(userService).saveTimePreferences(eq(testUserId), any(TimePreferenceRequest.class));

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/timetable/preferences/time", testUserId)
                        .with(csrf()) // CSRF 토큰 추가 (SecurityConfig에서 disable 안했다면 필수)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(timePreferenceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("시간 선호도 저장 성공")));
    }

    @Test
    @DisplayName("학점 목표 및 조합 설정 업데이트 API 테스트 - 성공")
    @WithMockUser
    void updateUserCreditAndCombinationPreferences_Success() throws Exception {
        // Arrange
        doNothing().when(userService).saveCreditAndCombinationPreferences(eq(testUserId), any(CreditSettingsRequest.class));

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/timetable/preferences/settings", testUserId) // 엔드포인트 확인
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditSettingsRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("학점 목표 및 강의 조합 설정 저장 성공")));
    }

    @Test
    @DisplayName("시간표 추천 API 테스트 - 성공 (결과 있음)")
    @WithMockUser
    void getRecommendedTimetables_Success_WithResults() throws Exception {
        // Arrange
        RecommendedTimetableDto mockTimetable1 = new RecommendedTimetableDto();
        mockTimetable1.setTimetableId(1);
        mockTimetable1.setScheduledCourses(List.of(
                new ScheduledCourseDto("CS101", "과목1", "전공", 3, "교수A", "101", "", Collections.emptyList())
        ));
        mockTimetable1.setTotalCredits(3);
        mockTimetable1.setCreditsByType(Map.of("전공", 3));

        List<RecommendedTimetableDto> mockRecommendations = List.of(mockTimetable1);
        when(timetableService.generateRecommendations(testUserId)).thenReturn(mockRecommendations);

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/timetable/recommendations", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables", hasSize(1)))
                .andExpect(jsonPath("$.timetables[0].timetableId", is(1)))
                .andExpect(jsonPath("$.timetables[0].totalCredits", is(3)))
                .andExpect(jsonPath("$.message", is(mockRecommendations.size() + "개의 시간표를 추천합니다.")));
    }

    @Test
    @DisplayName("시간표 추천 API 테스트 - 추천 결과 없음")
    @WithMockUser
    void getRecommendedTimetables_Success_NoResults() throws Exception {
        // Arrange
        when(timetableService.generateRecommendations(testUserId)).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/timetable/recommendations", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables", hasSize(0)))
                .andExpect(jsonPath("$.message", is("추천 가능한 시간표를 찾지 못했습니다. 조건을 변경하거나 필수 과목을 확인해주세요.")));
    }

    @Test
    @DisplayName("시간표 추천 API 테스트 - 필수과목 충돌 등으로 인한 IllegalArgumentException")
    @WithMockUser
    void getRecommendedTimetables_Fail_IllegalArgument() throws Exception {
        // Arrange
        String errorMessage = "필수 또는 재수강 과목 간에 시간이 중복됩니다.";
        when(timetableService.generateRecommendations(testUserId))
                .thenThrow(new IllegalArgumentException(errorMessage));

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/timetable/recommendations", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()) // 400 Bad Request
                .andExpect(jsonPath("$.message", is(errorMessage)));
    }
}