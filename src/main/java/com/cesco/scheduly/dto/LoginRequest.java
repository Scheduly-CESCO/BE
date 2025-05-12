package com.cesco.scheduly.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {
    private String student_id;
    private String password;
}
