package com.cesco.scheduly.dto.timetable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditSettingsRequest {
    @Schema(description = "유형별 학점 목표 설정. '전공', '교양', '이중전공' 등 자유롭게 키를 설정할 수 있습니다.",
            example = """
                    {
                        "전공": {"min": 9, "max": 12},
                        "교양": {"min": 3, "max": 6},
                        "이중전공": {"min": 6, "max": 6},
                        "부전공": {"min": 3, "max": 3}
                    }
                    """)
    // 사용자가 학점 범위를 설정한 과목 유형들 (예: "전공", "교양")과 각 범위
    // 예: {"전공": {"min":9, "max":12}, "교양": {"min":3, "max":6}}
    private Map<String, CreditRangeDto> creditGoalsPerType = new HashMap<>();

    // 사용자가 이번 학기에 수강하고자 하는 과목 유형의 '조합' (선택적 필드)
    // 이 필드가 null이거나 비어있다면, creditGoalsPerType에 설정된 모든 유형을 듣는 것으로 간주할 수 있음
    // 또는 사용자가 명시적으로 "전공, 교양만 듣겠다"와 같이 우선순위나 조합을 선택할 때 사용
    private List<String> courseTypeCombination;

    private Integer minTotalCredits;
    private Integer maxTotalCredits;
}