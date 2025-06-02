package com.cesco.scheduly.controller;

import com.cesco.scheduly.config.JwtTokenProvider;
import com.cesco.scheduly.dto.user.LoginRequest;
import com.cesco.scheduly.dto.user.LoginResponse;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.dto.user.SignupResponse;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "인증 API", description = "회원가입 및 로그인 기능 제공")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Operation(summary = "회원가입", description = "사용자 정보를 입력받아 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest dto) {
        User user = userService.signup(dto);
        return ResponseEntity.ok(new SignupResponse(user));
    }

    @Operation(summary = "로그인", description = "학생 ID와 비밀번호를 입력받아 JWT 토큰을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest dto) {
        User user = userService.authenticate(dto.getStudentId(), dto.getPassword());
        String token = jwtTokenProvider.createToken(user.getStudentId(), user.getId());
        return ResponseEntity.ok(new LoginResponse(token, user.getId(), user.getName()));
    }
}