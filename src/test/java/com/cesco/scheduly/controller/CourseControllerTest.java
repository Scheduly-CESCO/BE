package com.cesco.scheduly.controller;

import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.repository.CourseRepository;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import com.cesco.scheduly.service.DataInitializer;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// @TestInstance(TestInstance.Lifecycle.PER_CLASS) // 이 어노테이션을 삭제합니다.
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCourseSelectionRepository userCourseSelectionRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DataInitializer dataInitializer;

    private User testUser;

    // @BeforeAll -> @BeforeEach 로 변경합니다.
    // 이렇게 하면 각 @Test 메소드 실행 직전에 항상 DB를 깨끗하게 하고 시작합니다.
    @BeforeEach
    void setUp() {
        // DB에 강의 데이터가 없으면 초기화 (매번 count 쿼리가 실행되지만, 데이터가 있으면 skip)
        if (courseRepository.count() == 0) {
            dataInitializer.initializeData();
        }

        // 다른 테이블과의 연관 관계 때문에 생성의 역순으로 삭제합니다.
        userCourseSelectionRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성
        testUser = User.builder()
                .studentId("20251111_" + UUID.randomUUID())
                .name("테스트유저2")
                .passwordHash("hashed_password")
                .college(College.AI융합대학)
                .major("AI데이터융합전공")
                .doubleMajorType(DoubleMajorType.NONE)
                .grade(2).semester(1).build();
        userRepository.save(testUser);

    }

    @Test
    @WithMockUser
    @DisplayName("사용자의 필수/재수강 과목 시간 정보 조회 테스트")
    void should_return_time_info_for_user_selection_courses() throws Exception {
        // given: 사용자가 필수과목과 재수강과목을 미리 저장하는 상황
        Long userId = testUser.getId();
        List<String> requiredCourses = List.of("M01207101"); // 머신러닝
        List<String> retakeCourses = List.of("M01202101");   // 자료구조와알고리즘

        // PreferencesController의 API를 호출하여 데이터 저장
        mockMvc.perform(post("/preferences/required?userId=" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requiredCourses)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/preferences/retake?userId=" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retakeCourses)))
                .andExpect(status().isOk());

        // when: 핵심 기능인 시간 정보 조회 API 호출
        mockMvc.perform(get("/courses/user-selections")
                        .param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // 2개의 과목 정보가 반환되었는지 확인

                // ★★★ 핵심 수정 부분: jsonPath의 경로를 실제 JSON 키 이름으로 변경 ★★★
                .andExpect(jsonPath("$[0].학수번호").value("M01207101")) // courseCode -> 학수번호
                .andExpect(jsonPath("$[0].시간표정보[0].요일").value("Mon")) // scheduleSlots -> 시간표정보, day -> 요일
                .andExpect(jsonPath("$[0].시간표정보[0].startPeriod").value(4))
                .andExpect(jsonPath("$[0].시간표정보[0].endPeriod").value(6))
                .andExpect(jsonPath("$[1].학수번호").value("M01202101")) // courseCode -> 학수번호
                .andExpect(jsonPath("$[1].시간표정보[0].요일").value("Thu")) // scheduleSlots -> 시간표정보, day -> 요일
                .andExpect(jsonPath("$[1].시간표정보[0].startPeriod").value(4))
                .andExpect(jsonPath("$[1].시간표정보[0].endPeriod").value(6));
    }
}