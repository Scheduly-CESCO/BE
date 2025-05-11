package com.cesco.scheduly.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String studentId;

    private String passwordHash;

    private String name;

    private String major;
    private String doubleMajor;

    private int grade;
    private int semester;

    private LocalDateTime createdAt = LocalDateTime.now();
}
