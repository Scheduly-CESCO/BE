package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.dto.course.PreferencesRequest;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.exception.InvalidInputException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.service.CourseDataService;
import com.cesco.scheduly.service.Userservice;
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
import org.springframework.security.core.userdetails.User;
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
    private Userservice userService;

    @MockBean
    private CourseDataService courseDataService;

    private SignupRequest signupRequest;
    private User sampleRegisteredUser;
    private String testUserId;
    private PreferencesRequest preferenceRequest;

    // 실제 학수번호 형식 예시
    private String courseCodeSample1 = "M01207101";
    private String courseCodeSample2 = "G01001001";


    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest();
        signupRequest.setUsername("newUser");
        signupRequest.setPassword("password");
        signupRequest.setGrade("1");
        signupRequest.setMajor("Computer Science");
        signupRequest.setDouble_major(null);

        sampleRegisteredUser = User.builder()
                .userId("new-uuid-123")
                .username("newUser")
                .passwordHash("hashedPassword")
                .grade("1")
                .major("Computer Science")
                .build();

        testUserId = "user123";
        preferenceRequest = new PreferencesRequest();
        preferenceRequest.setCourseCodes(List.of(courseCodeSample1, courseCodeSample2));
    }

    @Test
    @DisplayName("사용자 회원가입 API 테스트 - 성공")
    void registerUser_Success() throws Exception {
        when(userService.registerUser(any(SignupRequest.class))).thenReturn(sampleRegisteredUser);

        mockMvc.perform(post("/api/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(sampleRegisteredUser.getUserId())))
                .andExpect(jsonPath("$.message", is("사용자 등록에 성공했습니다.")));
    }

    @Test
    @DisplayName("사용자 회원가입 API 테스트 - 실패 (사용자명 중복)")
    void registerUser_Fail_UsernameConflict() throws Exception {
        String existingUsername = signupRequest.getUsername();
        String errorMessage = "이미 사용 중인 사용자명입니다: " + existingUsername;
        when(userService.registerUser(any(SignupRequest.class)))
                .thenThrow(new InvalidInputException(errorMessage));

        mockMvc.perform(post("/api/users/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is(errorMessage)));
    }

    @Test
    @DisplayName("수강 이력 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateTakenCourses_Success() throws Exception {
        doNothing().when(userService).updateTakenCourses(eq(testUserId), anyList());

        mockMvc.perform(put("/api/users/{userId}/history/taken-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferenceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("수강 이력 업데이트 성공")));
    }

    @Test
    @DisplayName("수강 이력 업데이트 API 테스트 - 실패 (사용자 없음)")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateTakenCourses_Fail_UserNotFound() throws Exception {
        String nonExistentUserId = "nonExistentUser";
        String errorMessage = "사용자를 찾을 수 없습니다. ID: " + nonExistentUserId;

        Mockito.doThrow(new ResourceNotFoundException(errorMessage))
                .when(userService).updateTakenCourses(eq(nonExistentUserId), anyList());

        mockMvc.perform(put("/api/users/{userId}/history/taken-courses", nonExistentUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferenceRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is(errorMessage)));
    }

    @Test
    @DisplayName("수강 이력 조회 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void getTakenCourses_Success() throws Exception {
        List<String> mockCourseCodes = List.of(courseCodeSample1, courseCodeSample2);
        CourseInfo c1 = new CourseInfo(courseCodeSample1, "머신러닝", "전공", 3, "2");
        CourseInfo c2 = new CourseInfo(courseCodeSample2, "교양1", "교양", 3, "1");

        when(userService.getTakenCourses(testUserId)).thenReturn(mockCourseCodes);
        when(courseDataService.getCourseInfoByCode(courseCodeSample1)).thenReturn(c1);
        when(courseDataService.getCourseInfoByCode(courseCodeSample2)).thenReturn(c2);

        mockMvc.perform(get("/api/users/{userId}/history/taken-courses", testUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].courseCode", is(courseCodeSample1)))
                .andExpect(jsonPath("$.courses[1].courseCode", is(courseCodeSample2)));
    }

    @Test
    @DisplayName("필수 과목 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateMandatoryCourses_Success() throws Exception {
        doNothing().when(userService).updateMandatoryCourses(eq(testUserId), anyList());

        mockMvc.perform(put("/api/users/{userId}/semester/mandatory-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferenceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("필수 과목 업데이트 성공")));
    }

    @Test
    @DisplayName("재수강 과목 업데이트 API 테스트 - 성공")
    @WithMockUser(username="testuser", roles={"USER"})
    void updateRetakeCourses_Success() throws Exception {
        doNothing().when(userService).updateRetakeCourses(eq(testUserId), anyList());

        mockMvc.perform(put("/api/users/{userId}/semester/retake-courses", testUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferenceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("재수강 과목 업데이트 성공")));
    }
}