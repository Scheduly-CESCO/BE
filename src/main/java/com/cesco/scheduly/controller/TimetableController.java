package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.ApiResponse;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableDto;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableResponse;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.service.TimetableService;
import com.cesco.scheduly.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/timetable") // 사용자별 시간표 관련 API
public class TimetableController {

    private final TimetableService timetableService;
    private final UserService userService;

    public TimetableController(TimetableService timetableService, UserService userService) {
        this.timetableService = timetableService;
        this.userService = userService;
    }

    // 5단계: 수업 듣고 싶은 요일/시간대 필터 저장
    @PutMapping("/preferences/time")
    public ResponseEntity<ApiResponse> updateUserTimePreferences(
            @PathVariable String userId,
            @RequestBody TimePreferenceRequest timePreferenceRequest) {
        try {
            userService.saveTimePreferences(userId, timePreferenceRequest);
            return ResponseEntity.ok(new ApiResponse("시간 선호도 저장 성공"));
        } catch (IllegalArgumentException e) { // 사용자를 찾을 수 없는 경우 등
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간 선호도 저장 중 오류 발생: " + e.getMessage()));
        }
    }

    // 6, 7, 8단계: 수강할 학점 범위 및 조합 선택 저장
    @PutMapping("/preferences/credits-combination")
    public ResponseEntity<ApiResponse> updateUserCreditAndCombinationPreferences(
            @PathVariable String userId,
            @RequestBody CreditSettingsRequest creditSettingsRequest) {
        try {
            userService.saveCreditAndCombinationPreferences(userId, creditSettingsRequest);
            return ResponseEntity.ok(new ApiResponse("학점 및 강의 조합 설정 저장 성공"));
        } catch (IllegalArgumentException e) { // 사용자를 찾을 수 없는 경우 등
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("학점/조합 설정 저장 중 오류 발생: " + e.getMessage()));
        }
    }

    // 9단계: 추천 시간표 생성 요청
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendedTimetables(@PathVariable String userId) {
        try {
            List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(userId);
            if (recommendations == null || recommendations.isEmpty()) {
                return ResponseEntity.ok(new RecommendedTimetableResponse(Collections.emptyList(),"추천 가능한 시간표를 찾지 못했습니다. 조건을 변경해보세요."));
            }
            return ResponseEntity.ok(new RecommendedTimetableResponse(recommendations, recommendations.size() + "개의 시간표를 추천합니다."));
        } catch (IllegalArgumentException e) { // 사용자를 찾을 수 없거나, 필수과목 충돌 등 로직상 명시적 예외
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            // 실제 운영시에는 상세 오류를 로깅하고, 사용자에게는 일반적인 오류 메시지 전달
            System.err.println("추천 생성 중 심각한 오류: " + e.getMessage()); // 서버 로그에 상세 오류 기록
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간표 추천 중 내부 서버 오류가 발생했습니다."));
        }
    }
}