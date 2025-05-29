package com.cesco.scheduly.dto;

import com.cesco.scheduly.entity.UserEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class SignupResponse {
    private Long id;

    @JsonProperty("student_id")
    private String studentId;
    private String name;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public SignupResponse(UserEntity user) {
        this.id = user.getId();
        this.studentId = user.getStudentId();
        this.name = user.getName();
        this.createdAt = user.getCreatedAt();
    }
}
