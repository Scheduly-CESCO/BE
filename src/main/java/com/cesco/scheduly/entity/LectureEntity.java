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

    @Column(name = "학수번호", nullable = false, unique = true)
    private String lectureCode;

    @Column(name = "강의명", nullable = false)
    private String lectureName;

    @Column(name = "학과")
    private String department;

    @Column(name = "세부전공")
    private String subject;
}