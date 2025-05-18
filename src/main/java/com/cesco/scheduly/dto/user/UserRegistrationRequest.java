package com.cesco.scheduly.dto.user;

import lombok.Data;

@Data
public class UserRegistrationRequest {
    private String username;
    private String password;
    private String grade; // 예: "1학년", "2학년" 또는 간단히 "1", "2"
    private String major; // 주전공
    private String doubleMajor; // 이중전공 (선택)
}