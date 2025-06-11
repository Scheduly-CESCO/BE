package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyPageResponse {
    private String name;
    private String studentId;
    private College college; // ❗ enum
    private String major;
    private String doubleMajor;
    private DoubleMajorType doubleMajorType; // ❗ enum
    private String module1;
    private String module2;
    private String module3;
    private int grade;
    private int semester;
}