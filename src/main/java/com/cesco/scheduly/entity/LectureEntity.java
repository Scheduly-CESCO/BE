package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lectures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String area;          // 개설영역
    private String code;          // 학수번호
    private String name;          // 강의명
    private String professor;     // 교수명
}