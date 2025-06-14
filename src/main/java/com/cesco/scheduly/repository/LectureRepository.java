package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.LectureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureRepository extends JpaRepository<LectureEntity, Long> {
}