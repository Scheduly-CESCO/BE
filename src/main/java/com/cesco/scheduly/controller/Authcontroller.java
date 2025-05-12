package com.cesco.scheduly.controller;

import com.cesco.scheduly.dto.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.service.UserService;
import com.cesco.scheduly.config.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class Authcontroller {
    
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest dto) {
        User user = userService.signup(dto);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "student_id", user.getStudentId(),
                "name", user.getName(),
                "created_at", user.getCreatedAt()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest dto) {
        User user = userService.authenticate(dto.getStudent_id(), dto.getPassword());
        String token = jwtTokenProvider.createToken(user.getStudentId(), user.getId());
        return ResponseEntity.ok(new LoginResponse(token, user.getId(), user.getName()));
    }
}
