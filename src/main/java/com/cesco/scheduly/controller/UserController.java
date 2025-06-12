package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.user.MyPageResponse;
import com.cesco.scheduly.dto.user.MyPageUpdateRequest;
import com.cesco.scheduly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}/mypage")
    public ResponseEntity<MyPageResponse> getMyPage(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getMyPageInfo(id));
    }

    @PutMapping("/{id}/mypage")
    public ResponseEntity<?> updateMyPage(@PathVariable Long id, @RequestBody MyPageUpdateRequest dto) {
        userService.updateMyPageInfo(id, dto);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }
}
