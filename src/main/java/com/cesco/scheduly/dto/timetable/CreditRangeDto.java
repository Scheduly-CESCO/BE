package com.cesco.scheduly.dto.timetable;

import lombok.AllArgsConstructor; // 모든 필드를 인자로 받는 생성자 추가
import lombok.Data;
import lombok.NoArgsConstructor;  // 기본 생성자 추가

@Data
@NoArgsConstructor   // 기본 생성자
@AllArgsConstructor  // 모든 필드를 인자로 받는 생성자
public class CreditRangeDto {
    private int min;
    private int max;
}