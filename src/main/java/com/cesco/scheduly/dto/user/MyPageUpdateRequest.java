package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class MyPageUpdateRequest {
    private String name;
    private String studentId;
    private College college;
    private String major;
    private String doubleMajor;
    private DoubleMajorType doubleMajorType;
    private String module1;
    private String module2;
    private String module3;
    private int grade;
    private int semester;
}