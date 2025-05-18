package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column
    private String grade;

    @Column
    private String major;

    @Column(name = "double_major")
    private String doubleMajor;

    // UserEntity가 저장될 때 UserCourseSelectionEntity와 UserPreferenceEntity도 함께 관리되도록 설정 (양방향 관계 시)
    // 단방향으로 UserService에서 관리해도 무방합니다. 여기서는 UserService에서 관리하는 것으로 가정하고 mappedBy는 생략합니다.
}