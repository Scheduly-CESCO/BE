package com.cesco.scheduly.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter @Setter
public class SignupRequest {
    @JsonProperty("student_id")
    private String studentId;

    private String password;
    private String name;
    private String major;
    private String double_major;
    private int grade;
    private int semester;
    }
