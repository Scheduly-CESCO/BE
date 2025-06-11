// src/main/java/com/cesco/scheduly/entity/CourseEntity.java
package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEntity {

    @Id
    @Column(name = "course_code", length = 20)
    private String courseCode; // 학수번호 (PK)

    @Column(name = "course_name", nullable = false)
    private String courseName; // 교과목명

    @Column(name = "department_original")
    private String departmentOriginal; // 원본 개설영역

    @Column(name = "specialized_major")
    private String specialized_major; // 세부전공 (예: "AI데이터융합전공", "러시아학과")

    @Column(name = "group_id", length = 20) // 동일과목 판단용
    private String groupId;

    @Column(name = "generalized_type")
    private String generalizedType; // 일반화된 과목 타입 (전공, 교양 등)

    @Column(nullable = false)
    private Integer credits; // 학점

    @Column
    private Integer totalHours; // 총 시간

    @Column
    private String grade; // 학년

    @Column
    private String professor; // 담당교수

    @Column
    private String classroom; // 강의실

    @Lob // 긴 텍스트 저장
    @Column
    private String remarks; // 비고

    @Column(name = "is_restricted_course")
    private boolean isRestrictedCourse; // 추천 제한 여부

    // JSON 문자열 대신 @OneToMany 관계로 시간표 정보를 관리합니다.
    @Builder.Default
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TimeSlotEntity> scheduleSlots = new ArrayList<>();
}