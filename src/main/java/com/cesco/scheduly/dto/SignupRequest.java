package com.cesco.scheduly.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SignupRequest {
    private String student_id;
    private String password;
    private String name;
    private String major;
    private String double_major;
    private int grade;
    private int semester;
    }
