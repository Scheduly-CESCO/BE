package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.entity.User;
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

    public SignupResponse(User user) {
        this.id = user.getId();
        this.studentId = user.getStudentId();
        this.name = user.getName();
        this.createdAt = user.getCreatedAt();
    }
}
