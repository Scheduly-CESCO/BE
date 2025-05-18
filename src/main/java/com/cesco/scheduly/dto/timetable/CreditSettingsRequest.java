package com.cesco.scheduly.dto.timetable;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CreditSettingsRequest {
    // 6단계: 전체 학점 목표 (선택적, 8단계에서 계산될 수도 있음)
    private Integer minTotalCredits;
    private Integer maxTotalCredits;

    // 7단계: 수강할 과목 조합 유형
    // 예: ["전공", "교양"] 또는 ["이중전공", "전공", "교양"]
    // 여기서 문자열은 '개설영역'과 매핑될 수 있는 일반화된 타입입니다.
    private List<String> courseTypeCombination;

    // 8단계: 선택된 조합 내 각 과목 유형별 학점 범위
    // Key: "전공", "교양", "이중전공" 등 (courseTypeCombination의 요소와 일치)
    // Value: {"min": 6, "max": 12}
    private Map<String, CreditRangeDto> creditRangesPerType;
}