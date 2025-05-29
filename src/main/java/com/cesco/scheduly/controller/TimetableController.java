package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.ApiResponse;
import com.cesco.scheduly.dto.timetable.*; // 모든 timetable DTO 포함
import com.cesco.scheduly.service.TimetableService;
import com.cesco.scheduly.service.Userservice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/timetable")
public class TimetableController {

    private final TimetableService timetableService;
    private final Userservice userService;

    public TimetableController(TimetableService timetableService, Userservice userService) {
        this.timetableService = timetableService;
        this.userService = userService;
    }

    // 5단계: 시간 선호도 저장 API (변경 없음)
    @PutMapping("/preferences/time")
    public ResponseEntity<ApiResponse> updateUserTimePreferences(
            @PathVariable String userId,
            @RequestBody TimePreferenceRequest timePreferenceRequest) {
        try {
            userService.saveTimePreferences(userId, timePreferenceRequest);
            return ResponseEntity.ok(new ApiResponse("시간 선호도 저장 성공"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간 선호도 저장 중 서버 오류 발생: " + e.getMessage()));
        }
    }

    // 7, 8단계 통합: 학점 목표 및 수강 희망 유형 설정 API (이전 /preferences/credits-combination 경로 유지 또는 변경)
    @PutMapping("/preferences/settings") // 엔드포인트 이름 변경 가능 (예: /preferences/academic-plan)
    public ResponseEntity<ApiResponse> updateUserAcademicPlanPreferences(
            @PathVariable String userId,
            @RequestBody CreditSettingsRequest creditSettingsRequest) { // DTO는 Map<String, CreditRangeDto> creditGoalsPerType 포함
        try {
            // creditSettingsRequest에 minTotalCredits, maxTotalCredits도 포함되어 있다고 가정
            userService.saveCreditAndCombinationPreferences(userId, creditSettingsRequest);
            return ResponseEntity.ok(new ApiResponse("학점 목표 및 강의 조합 설정 저장 성공"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("학점/조합 설정 저장 중 서버 오류 발생: " + e.getMessage()));
        }
    }

    // 9단계: 추천 시간표 생성 요청 API (변경 없음)
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendedTimetables(@PathVariable String userId) {
        try {
            List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(userId);
            if (recommendations == null || recommendations.isEmpty()) {
                return ResponseEntity.ok(new RecommendedTimetableResponse(Collections.emptyList(),"추천 가능한 시간표를 찾지 못했습니다. 조건을 변경하거나 필수 과목을 확인해주세요."));
            }
            return ResponseEntity.ok(new RecommendedTimetableResponse(recommendations, recommendations.size() + "개의 시간표를 추천합니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            System.err.println("추천 생성 중 심각한 오류 - User ID: " + userId + ", Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간표 추천 중 내부 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
}