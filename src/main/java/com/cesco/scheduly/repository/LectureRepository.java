package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.LectureEntity; // 팀원 LectureEntity 사용 가정
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LectureRepository extends JpaRepository<LectureEntity, Long> { // LectureEntity의 ID 타입에 따라 Long 또는 다른 타입
    // LectureDataService에서 사용된 findAllLectureIds()를 위한 메소드
    @Query("SELECT le.lectureCode FROM LectureEntity le") // LectureEntity에 lectureCode 필드가 있다고 가정
    List<String> findAllLectureIds();
}