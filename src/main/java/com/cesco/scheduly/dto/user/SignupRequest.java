package com.cesco.scheduly.dto.user;

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

    private String college;
    private String major;

    private String doubleMajorType; // 부전공 / 이중전공 / 전공심화 중 택1
    private String double_major;

    private List<String> fusionModules;  // 융인이 1전공일 경우 모듈 3개
    private List<String> fusionSubModule;  // 융인이 이중일 경우 모듈 2개

    private int grade;
    private int semester;

    private String module1;
    private String module2;
    private String module3;
    }
