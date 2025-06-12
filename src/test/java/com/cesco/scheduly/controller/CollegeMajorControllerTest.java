package com.cesco.scheduly.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// Spring Security 테스트 지원을 위한 import
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CollegeMajorController.class)
@WithMockUser
class CollegeMajorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("전체 단과대 목록 조회 성공 테스트")
    void getColleges_Success() throws Exception {
        // when
        ResultActions resultActions = mockMvc.perform(get("/api/university/colleges"));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(14))) // College.java 에 정의된 14개가 맞는지 확인
                .andExpect(jsonPath("$[0]", is("통번역대학")));
    }

    @Test
    @DisplayName("특정 단과대에 소속된 전공 목록 조회 성공 테스트")
    void getMajorsByCollege_Success() throws Exception {
        // given
        String collegeName = "공과대학";

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/university/majors")
                .param("collegeName", collegeName));

        // then
        resultActions
                .andExpect(status().isOk())
                // Major.java에 정의된 '공과대학' 소속 전공은 5개입니다.
                .andExpect(jsonPath("$", hasSize(5))) // <<-- 4에서 5로 수정!
                .andExpect(jsonPath("$", containsInAnyOrder(
                        "컴퓨터공학부",
                        "정보통신공학과",
                        "반도체전자공학부_반도체공학전공",
                        "반도체전자공학부_전자공학전공",
                        "산업경영공학과"
                )));
    }

    @Test
    @DisplayName("특수문자(&)가 포함된 단과대의 전공 목록 조회 성공 테스트")
    void getMajorsByCollege_WithSpecialChar_Success() throws Exception {
        // given
        String collegeName = "Culture&Technology융합대학";

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/university/majors")
                .param("collegeName", collegeName));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // Major.java 에 정의된 해당 단과대 전공 3개가 맞는지 확인
                .andExpect(jsonPath("$", containsInAnyOrder(
                        "글로벌스포츠산업학부",
                        "디지털콘텐츠학부",
                        "투어리즘_웰니스학부"
                )));
    }

    @Test
    @DisplayName("존재하지 않는 단과대 조회 시 빈 리스트 반환 테스트")
    void getMajorsByCollege_NotFound_ShouldReturnEmptyList() throws Exception {
        // given
        String collegeName = "존재하지않는단과대";

        // when
        ResultActions resultActions = mockMvc.perform(get("/api/university/majors")
                .param("collegeName", collegeName));

        // then
        // 컨트롤러는 없는 단과대일 경우 빈 리스트를 반환합니다.
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}