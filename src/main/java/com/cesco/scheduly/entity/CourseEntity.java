package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 이 엔티티는 CourseDataService가 JSON에서 읽은 내용을 DB에 저장하고 관리할 때 사용됩니다.
// JSON을 매번 읽는 대신 DB를 사용하면 성능 및 데이터 관리에 이점이 있습니다.
// 시간표 정보는 복잡하므로 별도 테이블(OneToMany) 또는 JSON 문자열로 저장 가능합니다.

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

    @Column(name = "generalized_type")
    private String generalizedType; // 일반화된 과목 타입 (전공, 교양 등)

    @Column(nullable = false)
    private Integer credits; // 학점

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

    // 시간표 정보 (List<TimeSlotDto>)를 JSON 문자열로 저장하는 예시
    @Lob
    @Column(name = "schedule_slots_json", columnDefinition = "TEXT")
    private String scheduleSlotsJson;
    // 또는 @OneToMany 관계를 사용하여 TimeSlotEntity를 별도로 만들 수 있습니다. (더 정규화된 방식)
}