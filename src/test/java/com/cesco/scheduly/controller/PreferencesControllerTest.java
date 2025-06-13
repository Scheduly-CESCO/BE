package com.cesco.scheduly.controller;

import com.cesco.scheduly.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(PreferencesController.class)
class PreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("✅ 기수강 과목 관련")
    class CompletedLectures {

        @Test
        @DisplayName("기수강 과목 추가")
        void addCompletedLectures() throws Exception {
            List<String> lectures = List.of("ENG101", "MATH202");

            mockMvc.perform(post("/preferences/completed")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("completed_saved"))
                    .andDo(print());

            Mockito.verify(userService).updateTakenCourses("1", lectures);
        }

        @Test
        @DisplayName("기수강 과목 삭제")
        void removeCompletedLectures() throws Exception {
            List<String> lectures = List.of("ENG101");

            mockMvc.perform(delete("/preferences/completed")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("completed_removed"))
                    .andDo(print());

            Mockito.verify(userService).removeTakenCourses(1L, lectures);
        }
    }

    @Nested
    @DisplayName("✅ 필수 과목 관련")
    class RequiredLectures {

        @Test
        @DisplayName("필수 과목 추가")
        void addRequiredLectures() throws Exception {
            List<String> lectures = List.of("COMP201");

            mockMvc.perform(post("/preferences/required")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("required_saved"))
                    .andDo(print());

            Mockito.verify(userService).updateMandatoryCourses("1", lectures);
        }

        @Test
        @DisplayName("필수 과목 삭제")
        void removeRequiredLectures() throws Exception {
            List<String> lectures = List.of("COMP201");

            mockMvc.perform(delete("/preferences/required")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("required_removed"))
                    .andDo(print());

            Mockito.verify(userService).removeMandatoryCourses(1L, lectures);
        }
    }

    @Nested
    @DisplayName("✅ 재수강 과목 관련")
    class RetakeLectures {

        @Test
        @DisplayName("재수강 과목 추가")
        void addRetakeLectures() throws Exception {
            List<String> lectures = List.of("BIO101");

            mockMvc.perform(post("/preferences/retake")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("retake_saved"))
                    .andDo(print());

            Mockito.verify(userService).updateRetakeCourses("1", lectures);
        }

        @Test
        @DisplayName("재수강 과목 삭제")
        void removeRetakeLectures() throws Exception {
            List<String> lectures = List.of("BIO101");

            mockMvc.perform(delete("/preferences/retake")
                            .param("userId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lectures)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("retake_removed"))
                    .andDo(print());

            Mockito.verify(userService).removeRetakeCourses(1L, lectures);
        }
    }
}