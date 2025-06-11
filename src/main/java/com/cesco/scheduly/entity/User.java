package com.cesco.scheduly.entity;

import com.cesco.scheduly.enums.College;
import com.cesco.scheduly.enums.DoubleMajorType;
import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private College college;

    @Column(nullable = false)
    private String major; // 주전공

    private String doubleMajor; // 이중전공 (선택 사항이므로 nullable=true 기본값)

    @Enumerated(EnumType.STRING)
    private DoubleMajorType doubleMajorType;

    @Column(nullable = false)
    private int grade; // 학년

    @Column(nullable = false)
    private int semester; // 학기

    private String module1;
    private String module2;
    private String module3;

    @Builder.Default // 빌더 사용 시 기본값 설정
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public int getAdmissionYear() {
        try {
            return Integer.parseInt(this.studentId.substring(0,4));
        } catch (Exception e) {
            throw new IllegalStateException("학번의 형식이 잘못되었습니다: " + studentId);
        }
    }
}