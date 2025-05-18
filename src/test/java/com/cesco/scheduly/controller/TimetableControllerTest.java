package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableDto;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = TimetableController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}) // 테스트를 위해 Security 자동 설정 제외 시도
public class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TimetableService timetableService;

    @MockBean
    private UserService userService; // TimetableController가 UserService도 사용

    private String testUserId;
    private TimePreferenceRequest timePreferenceRequest;
    private CreditSettingsRequest creditSettingsRequest;

    @BeforeEach
    void setUp() {
        testUserId = "user123";

        timePreferenceRequest = new TimePreferenceRequest();
        timePreferenceRequest.setAvoidDays(List.of("Fri"));
        // 다른 필드들도 필요에 따라 설정

        creditSettingsRequest = new CreditSettingsRequest();
        creditSettingsRequest.setMinTotalCredits(15);
        creditSettingsRequest.setMaxTotalCredits(21);
        creditSettingsRequest.setCourseTypeCombination(List.of("전공", "교양"));
        creditSettingsRequest.setCreditRangesPerType(Map.of(
                "전공", new CreditRangeDto(9, 12),
                "교양", new CreditRangeDto(6, 9)
        ));
    }


    @Test
    @DisplayName("시간 선호도 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"}) // 가상 사용자 인증 (CSRF와 별개로 인증 필요시)
    void updateUserTimePreferences_Success() throws Exception {
        // Arrange
        // userService.saveTimePreferences가 void를 반환하므로 doNothing() 사용
        doNothing().when(userService).saveTimePreferences(anyString(), any(TimePreferenceRequest.class));

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/timetable/preferences/time", testUserId)
                        .with(csrf()) // CSRF 토큰 추가 (403 Forbidden 방지)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(timePreferenceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("시간 선호도 저장 성공")));
    }

    @Test
    @DisplayName("학점 및 조합 설정 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateUserCreditAndCombinationPreferences_Success() throws Exception {
        // Arrange
        doNothing().when(userService).saveCreditAndCombinationPreferences(anyString(), any(CreditSettingsRequest.class));

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/timetable/preferences/credits-combination", testUserId)
                        .with(csrf()) // CSRF 토큰 추가
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditSettingsRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("학점 및 강의 조합 설정 저장 성공")));
    }


    @Test
    @DisplayName("시간표 추천 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"}) // 인증된 사용자 설정
    void getRecommendedTimetables_Success() throws Exception {
        // Arrange
        RecommendedTimetableDto mockTimetable = new RecommendedTimetableDto(); // 테스트용 데이터 채우기
        mockTimetable.setTimetableId(1);
        mockTimetable.setScheduledCourses(Collections.emptyList()); // 예시
        mockTimetable.setCreditsByType(Collections.emptyMap());
        mockTimetable.setTotalCredits(0);

        List<RecommendedTimetableDto> mockRecommendations = List.of(mockTimetable);

        when(timetableService.generateRecommendations(testUserId)).thenReturn(mockRecommendations);

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/timetable/recommendations", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables", hasSize(1)))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("시간표 추천 API 테스트 - 추천 결과 없음")
    @WithMockUser(username="testuser", roles={"USER"})
    void getRecommendedTimetables_NoResults() throws Exception {
        // Arrange
        when(timetableService.generateRecommendations(testUserId)).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/timetable/recommendations", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetables", hasSize(0)))
                .andExpect(jsonPath("$.message", is("추천 가능한 시간표를 찾지 못했습니다. 조건을 변경하거나 필수 과목을 확인해주세요.")));
    }
}