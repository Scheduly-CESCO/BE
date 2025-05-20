package com.cesco.scheduly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {

    @JsonProperty("student_id")
    private String studentId;
    private String password;
}