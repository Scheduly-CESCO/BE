package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.LectureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LectureRepository extends JpaRepository<LectureEntity, Long> {

    // 전체 학수번호 조회용
    List<String> findAllLectureCodes();

    // 특정 prefix 조건에 맞는 강의 코드만 조회
    List<LectureEntity> findByLectureCodeStartingWith(String prefix);

    // 여러 prefix가 들어왔을 때 처리
    List<LectureEntity> findByLectureCodeIn(List<String> codes);
}