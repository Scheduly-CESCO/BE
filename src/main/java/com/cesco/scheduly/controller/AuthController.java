package com.cesco.scheduly.controller;

import com.cesco.scheduly.config.JwtTokenProvider;
import com.cesco.scheduly.dto.user.LoginRequest;
import com.cesco.scheduly.dto.user.LoginResponse;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.dto.user.SignupResponse;
import com.cesco.scheduly.service.UserService;
import com.cesco.scheduly.entity.User; // 정확한 User 엔티티 import

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest dto) {
        User user = userService.signup(dto);
        return ResponseEntity.ok(new SignupResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest dto) {
        User user = userService.authenticate(dto.getStudentId(), dto.getPassword());
        String token = jwtTokenProvider.createToken(user.getStudentId(), user.getId());
        return ResponseEntity.ok(new LoginResponse(token, user.getId(), user.getName()));
    }
}