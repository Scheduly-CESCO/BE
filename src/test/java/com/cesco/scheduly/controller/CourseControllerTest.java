package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.service.CourseDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest에 SecurityAutoConfiguration을 제외하도록 설정
@WebMvcTest(controllers = CourseController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseDataService courseDataService; // CourseController의 의존성이므로 모킹

    @Test
    @DisplayName("강의 검색 API 테스트 - 성공 (결과 있음)")
    void searchCourses_WhenCoursesFound_ShouldReturnCourseList() throws Exception {
        // Arrange
        CourseInfo course1 = new CourseInfo("CS101", "프로그래밍 기초", "컴퓨터공학부", 3, "1");
        CourseInfo course2 = new CourseInfo("MA202", "미적분학1", "수학과", 3, "1");
        List<CourseInfo> mockResults = List.of(course1, course2);

        // CourseDataService의 searchCourses 메소드가 어떤 인자로 호출되든 mockResults를 반환하도록 설정
        when(courseDataService.searchCourses(anyString(), anyString(), anyString())).thenReturn(mockResults);

        // Act & Assert
        mockMvc.perform(get("/api/courses/search")
                        .param("q", "프로그래밍")
                        .param("department", "컴퓨터공학부")
                        .param("grade", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // HTTP 상태 코드 200 (OK) 기대
                .andExpect(jsonPath("$.courses", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].courseCode", is("CS101")))
                .andExpect(jsonPath("$.courses[1].courseName", is("미적분학1"))); // 필드명 수정

        // courseDataService의 searchCourses 메소드가 올바른 인자로 호출되었는지 확인
        verify(courseDataService).searchCourses("프로그래밍", "컴퓨터공학부", "1");
    }

    @Test
    @DisplayName("강의 검색 API 테스트 - 성공 (결과 없음)")
    void searchCourses_WhenNoCoursesFound_ShouldReturnEmptyList() throws Exception {
        // Arrange
        when(courseDataService.searchCourses(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/courses/search")
                        .param("q", "매우특이한검색어")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(0)));

        verify(courseDataService).searchCourses("매우특이한검색어", null, null); // department, grade는 null로 전달될 것
    }

    @Test
    @DisplayName("강의 검색 API 테스트 - 파라미터 없이 호출")
    void searchCourses_WhenNoParameters_ShouldReturnAppropriateResults() throws Exception {
        // Arrange
        CourseInfo course1 = new CourseInfo("CS101", "프로그래밍 기초", "컴퓨터공학부", 3, "1");
        List<CourseInfo> allCoursesMock = List.of(course1);

        when(courseDataService.searchCourses(isNull(), isNull(), isNull())).thenReturn(allCoursesMock);

        // Act & Assert
        mockMvc.perform(get("/api/courses/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].courseCode", is("CS101")));

        verify(courseDataService).searchCourses(isNull(), isNull(), isNull());
    }
}