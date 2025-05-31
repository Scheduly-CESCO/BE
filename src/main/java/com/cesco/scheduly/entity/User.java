package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.*; // Getter, Setter, NoArgsConstructor, AllArgsConstructor, Builder 모두 포함

import java.time.LocalDateTime;

@Entity
@Table(name = "users") // 명시적으로 테이블명 지정 권장
@Getter
@Setter
@NoArgsConstructor // JPA 및 Lombok Builder를 위해 필요
@AllArgsConstructor // 모든 필드를 갖는 생성자
@Builder // 빌더 패턴 사용을 위해
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false) // 학번은 필수, 고유값
    private String studentId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String major; // 주전공

    private String doubleMajor; // 이중전공 (선택 사항이므로 nullable=true 기본값)

    @Column(nullable = false)
    private int grade; // 학년

    @Column(nullable = false)
    private int semester; // 학기

    @Builder.Default // 빌더 사용 시 기본값 설정
    private LocalDateTime createdAt = LocalDateTime.now();
}