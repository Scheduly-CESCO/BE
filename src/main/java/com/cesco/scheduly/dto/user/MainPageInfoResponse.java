package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MainPageInfoResponse {
    private String name;
    private String studentId;
    private College college;
    private String major;
    private DoubleMajorType doubleMajorType;
    private String doubleMajor;
}