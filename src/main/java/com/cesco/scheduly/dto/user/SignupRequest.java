package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.FusionMajorModule;
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
    @JsonProperty("double_major")
    private String doubleMajor;

    @JsonProperty("double_major_type")
    private String doubleMajorType;

    private List<String> modules;
    private FusionMajorModule module1;
    private FusionMajorModule module2;
    private FusionMajorModule module3;

    private int grade;
    private int semester;

    }
