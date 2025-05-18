package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.CourseListRequest;
import com.cesco.scheduly.dto.user.UserRegistrationRequest;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.exception.InvalidInputException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(controllers = UserController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private CourseDataService courseDataService;

    private UserRegistrationRequest userRegistrationRequest;
    private UserEntity sampleRegisteredUser;
    private String testUserId;
    private CourseListRequest courseListRequest;


    @BeforeEach
    void setUp() {
        userRegistrationRequest = new UserRegistrationRequest();
        userRegistrationRequest.setUsername("newUser");
        userRegistrationRequest.setPassword("password");
        userRegistrationRequest.setGrade("1");
        userRegistrationRequest.setMajor("Computer Science");
        userRegistrationRequest.setDoubleMajor(null);

        sampleRegisteredUser = UserEntity.builder()
                .userId("new-uuid-123")
                .username("newUser")
                .passwordHash("hashedPassword")
                .grade("1")
                .major("Computer Science")
                .build();

        testUserId = "user123";
        courseListRequest = new CourseListRequest();
        courseListRequest.setCourseCodes(List.of("CS101", "MA202"));
    }

    // 1. 사용자 등록 API
    @Test
    @DisplayName("사용자 회원가입 API 테스트 - 성공")
    void registerUser_Success() throws Exception {
        // Arrange
        when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(sampleRegisteredUser);

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegistrationRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(sampleRegisteredUser.getUserId())))
                .andExpect(jsonPath("$.message", is("사용자 등록에 성공했습니다.")));
    }

    @Test
    @DisplayName("사용자 회원가입 API 테스트 - 실패 (사용자명 중복)")
    void registerUser_Fail_UsernameConflict() throws Exception {
        // Arrange
        String existingUsername = userRegistrationRequest.getUsername();
        String errorMessage = "이미 사용 중인 사용자명입니다: " + existingUsername;
        when(userService.registerUser(any(UserRegistrationRequest.class)))
                .thenThrow(new InvalidInputException(errorMessage));

        // Act & Assert
        mockMvc.perform(post("/api/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegistrationRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is(errorMessage)));
    }


    // 2. 사용자가 이전에 수강한 과목 업데이트 API
    @Test
    @DisplayName("수강 이력 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateTakenCourses_Success() throws Exception {
        // Arrange
        doNothing().when(userService).updateTakenCourses(eq(testUserId), anyList());

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/history/taken-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseListRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("수강 이력 업데이트 성공")));
    }

    @Test
    @DisplayName("수강 이력 업데이트 API 테스트 - 실패 (사용자 없음)")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateTakenCourses_Fail_UserNotFound() throws Exception {
        // Arrange
        String nonExistentUserId = "nonExistentUser";
        String errorMessage = "사용자를 찾을 수 없습니다. ID: " + nonExistentUserId;

        // userService.updateTakenCourses 메소드가 nonExistentUserId로 호출될 때
        // ResourceNotFoundException을 던지도록 모킹합니다.
        Mockito.doThrow(new ResourceNotFoundException(errorMessage))
                .when(userService).updateTakenCourses(eq(nonExistentUserId), anyList());
        // 또는, UserSerivce 내부의 getUserCourseSelection이 예외를 던지도록 모킹할 수도 있습니다.
        // when(userService.getUserCourseSelection(eq(nonExistentUserId)))
        //     .thenThrow(new ResourceNotFoundException(errorMessage));


        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/history/taken-courses", nonExistentUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseListRequest)))
                .andExpect(status().isNotFound()) // 404 Not Found 기대
                .andExpect(jsonPath("$.message", is(errorMessage)));
    }


    // 사용자가 이전에 수강한 과목 조회 API (4단계 재수강 선택 시 프론트엔드에서 사용)
    @Test
    @DisplayName("수강 이력 조회 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void getTakenCourses_Success() throws Exception {
        // Arrange
        List<String> mockCourseCodes = List.of("CS101", "MA202");
        CourseInfo cs101 = new CourseInfo("CS101", "프로그래밍 기초", "컴퓨터공학부", 3, "1");
        CourseInfo ma202 = new CourseInfo("MA202", "미적분학1", "수학과", 3, "1");

        when(userService.getTakenCourses(testUserId)).thenReturn(mockCourseCodes);
        when(courseDataService.getCourseInfoByCode("CS101")).thenReturn(cs101);
        when(courseDataService.getCourseInfoByCode("MA202")).thenReturn(ma202);

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/history/taken-courses", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].courseCode", is("CS101")))
                .andExpect(jsonPath("$.courses[1].courseCode", is("MA202")));
    }

    // 3. 이번 학기 필수 과목 업데이트 API
    @Test
    @DisplayName("필수 과목 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateMandatoryCourses_Success() throws Exception {
        // Arrange
        doNothing().when(userService).updateMandatoryCourses(eq(testUserId), anyList());

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/semester/mandatory-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseListRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("필수 과목 업데이트 성공")));
    }

    // 4. 이번 학기 재수강 과목 업데이트 API
    @Test
    @DisplayName("재수강 과목 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateRetakeCourses_Success() throws Exception {
        // Arrange
        doNothing().when(userService).updateRetakeCourses(eq(testUserId), anyList());

        // Act & Assert
        mockMvc.perform(put("/api/users/{userId}/semester/retake-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseListRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("재수강 과목 업데이트 성공")));
    }
}