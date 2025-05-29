package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.service.Userservice;
import com.cesco.scheduly.config.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Userservice userService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(Userservice userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest dto) {
        UserEntity user = userService.signup(dto);
        return ResponseEntity.ok(new SignupResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest dto) {
        UserEntity user = userService.authenticate(dto.getStudentId(), dto.getPassword());
        String token = jwtTokenProvider.createToken(user.getStudentId(), user.getId());
        return ResponseEntity.ok(new LoginResponse(token, user.getId(), user.getName()));
    }
}