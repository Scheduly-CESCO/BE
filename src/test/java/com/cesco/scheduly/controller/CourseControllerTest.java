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

@WebMvcTest(controllers = CourseController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseDataService courseDataService;

    // 실제 학수번호 형식 예시
    private String courseCodeSample1 = "M01207101";
    private String courseCodeSample2 = "G01001001";

    @Test
    @DisplayName("강의 검색 API 테스트 - 성공 (결과 있음)")
    void searchCourses_WhenCoursesFound_ShouldReturnCourseList() throws Exception {
        // Arrange
        CourseInfo course1 = new CourseInfo(courseCodeSample1, "머신러닝", "전공", 3, "2");
        CourseInfo course2 = new CourseInfo(courseCodeSample2, "교양1", "교양", 3, "1");
        List<CourseInfo> mockResults = List.of(course1, course2);

        when(courseDataService.searchCourses(anyString(), anyString(), anyString())).thenReturn(mockResults);

        // Act & Assert
        mockMvc.perform(get("/api/courses/search")
                        .param("q", "머신")
                        .param("department", "전공")
                        .param("grade", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].courseCode", is(courseCodeSample1)))
                .andExpect(jsonPath("$.courses[0].courseName", is("머신러닝")))
                .andExpect(jsonPath("$.courses[1].courseCode", is(courseCodeSample2)));

        verify(courseDataService).searchCourses("머신", "전공", "2");
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

        verify(courseDataService).searchCourses("매우특이한검색어", null, null);
    }

    @Test
    @DisplayName("강의 검색 API 테스트 - 파라미터 없이 호출")
    void searchCourses_WhenNoParameters_ShouldReturnAppropriateResults() throws Exception {
        // Arrange
        CourseInfo course1 = new CourseInfo(courseCodeSample1, "머신러닝", "전공", 3, "2");
        List<CourseInfo> allCoursesMock = List.of(course1);

        when(courseDataService.searchCourses(isNull(), isNull(), isNull())).thenReturn(allCoursesMock);

        // Act & Assert
        mockMvc.perform(get("/api/courses/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].courseCode", is(courseCodeSample1)));

        verify(courseDataService).searchCourses(isNull(), isNull(), isNull());
    }
}