package com.cesco.scheduly.dto.course;

import com.cesco.scheduly.entity.LectureEntity;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LectureDto {
    private Long id;
    private String area;
    private String name;
    private String code;
    private String professor;

    public static LectureDto from(LectureEntity lectureEntity) {
        return LectureDto.builder()
                .id(lectureEntity.getId())
                .area(lectureEntity.getArea())
                .code(lectureEntity.getCode())
                .name(lectureEntity.getName())
                .professor(lectureEntity.getProfessor())
                .build();
    }
}
