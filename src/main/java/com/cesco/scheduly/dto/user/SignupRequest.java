package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.enums.College;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Getter @Setter
public class SignupRequest {
    @JsonProperty("student_id")
    private String studentId;
    private String password;
    private String name;

    private College college;
    private College parseCollege(String collegeName) {
        try {
            return College.valueOf(collegeName.replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid college name: " + collegeName);
        }
    }
    private String major;

    @Getter
    private String double_major_type; // 부전공 / 이중전공 / 전공심화 중 택1
    private String double_major;

    private List<String> modules;

    private int grade;
    private int semester;

    }
