package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.ApiResponse;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableDto;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableResponse;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.exception.MandatoryCourseConflictException;
import com.cesco.scheduly.service.TimetableService;
import com.cesco.scheduly.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/timetable") // userId 경로 변수 사용
public class TimetableController {

    private final TimetableService timetableService;
    private final UserService userService; // 대문자 S로 된 UserService 사용 가정

    private static final Logger logger = LoggerFactory.getLogger(TimetableController.class);

    @Autowired // 생성자 주입
    public TimetableController(TimetableService timetableService, UserService userService) {
        this.timetableService = timetableService;
        this.userService = userService;
    }

    // 5단계: 시간 선호도 저장 API
    @PutMapping("/preferences/time")
    public ResponseEntity<ApiResponse> updateUserTimePreferences(
            @PathVariable Long userId, // Long 타입으로 변경
            @RequestBody TimePreferenceRequest timePreferenceRequest) {
        try {
            userService.saveTimePreferences(userId, timePreferenceRequest);
            return ResponseEntity.ok(new ApiResponse("시간 선호도 저장 성공"));
        } catch (Exception e) { // 더 구체적인 예외 처리 가능 (예: ResourceNotFoundException)
            // logger.error("시간 선호도 저장 중 오류 발생 - User ID: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간 선호도 저장 중 오류 발생: " + e.getMessage()));
        }
    }

    // 7, 8단계 통합: 학점 목표 및 수강 희망 유형 설정 API
    @PutMapping("/preferences/settings")
    public ResponseEntity<ApiResponse> updateUserAcademicPlanPreferences(
            @PathVariable Long userId, // Long 타입으로 변경
            @RequestBody CreditSettingsRequest creditSettingsRequest) {
        try {
            userService.saveCreditAndCombinationPreferences(userId, creditSettingsRequest);
            return ResponseEntity.ok(new ApiResponse("학점 목표 및 강의 조합 설정 저장 성공"));
        }
        catch (IllegalArgumentException e) {
            // logger.warn("잘못된 학점 설정 요청 - User ID: {}, 오류: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        }
        catch (Exception e) {
            // logger.error("학점/조합 설정 저장 중 오류 발생 - User ID: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("학점/조합 설정 저장 중 오류 발생: " + e.getMessage()));
        }
    }

    // 9단계: 추천 시간표 생성 요청 API
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendedTimetables(@PathVariable Long userId) { // Long 타입으로 변경
        try {
            List<RecommendedTimetableDto> recommendations = timetableService.generateRecommendations(userId);
            if (recommendations == null || recommendations.isEmpty()) {
                // 팀원이 구현한 다른 컨트롤러에서 Map.of("message", "...") 형태를 사용했다면 일관성 유지 가능
                return ResponseEntity.ok(new RecommendedTimetableResponse(Collections.emptyList(),"추천 가능한 시간표를 찾지 못했습니다. 조건을 변경하거나 필수 과목을 확인해주세요."));
            }
            return ResponseEntity.ok(new RecommendedTimetableResponse(recommendations, recommendations.size() + "개의 시간표를 추천합니다."));
        }
        catch (MandatoryCourseConflictException e) {
            logger.warn("시간표 추천 요청 처리 중 필수 과목 충돌 - User ID: {}, 오류: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        }
        catch (IllegalArgumentException e) { // 서비스 단에서 던지는 명시적인 예외
            logger.warn("시간표 추천 요청 처리 중 잘못된 인자 - User ID: {}, 오류: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("추천 생성 중 심각한 오류 - User ID: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("시간표 추천 중 내부 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
}