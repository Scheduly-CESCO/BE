package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.timetable.MainPageTimetableResponse;
import com.cesco.scheduly.dto.user.MainPageInfoResponse;
import com.cesco.scheduly.service.TimetableService;
import com.cesco.scheduly.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메인페이지 API", description = "메인페이지에 필요한 정보 제공")
@RestController
@RequestMapping("/users/{userId}/main")
@RequiredArgsConstructor
public class MainPageController {

    private final UserService userService;
    private final TimetableService timetableService;

    @Operation(summary = "메인페이지 상단 사용자 정보 조회")
    @GetMapping("/info")
    public ResponseEntity<MainPageInfoResponse> getMainPageInfo(@PathVariable Long userId) {
        MainPageInfoResponse userInfo = userService.getMainPageInfo(userId);
        return ResponseEntity.ok(userInfo);
    }

    @Operation(summary = "메인페이지 시간표/콘텐츠 조회")
    @GetMapping("/content")
    public ResponseEntity<MainPageTimetableResponse> getMainPageContent(@PathVariable Long userId) {
        MainPageTimetableResponse timetableContent = timetableService.getMainPageTimetable(userId);
        return ResponseEntity.ok(timetableContent);
    }
}