package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_course_selections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCourseSelectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "selection_id")
    private String selectionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true) // User 엔티티의 PK 'id'(Long) 참조
    private User user; // 팀원 User 엔티티 사용

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_taken_courses", joinColumns = @JoinColumn(name = "selection_id"))
    @Column(name = "course_code")
    @Builder.Default
    private List<String> takenCourses = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_mandatory_courses", joinColumns = @JoinColumn(name = "selection_id"))
    @Column(name = "course_code")
    @Builder.Default
    private List<String> mandatoryCourses = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_retake_courses", joinColumns = @JoinColumn(name = "selection_id"))
    @Column(name = "course_code")
    @Builder.Default
    private List<String> retakeCourses = new ArrayList<>();
}