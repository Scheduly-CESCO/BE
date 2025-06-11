package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<CourseEntity, String> { // 엔티티와 PK 타입을 명시

    /**
     * 동적 검색 쿼리. 과목명/학수번호, 개설영역, 학년으로 강의를 검색합니다.
     * :query, :department, :grade 파라미터가 null이면 해당 조건은 무시됩니다.
     * 기존 CourseDataService.searchCourses() 메소드를 대체합니다.
     *
     * @param query      검색어 (과목명 또는 학수번호)
     * @param department 필터링할 개설영역
     * @param grade      필터링할 학년
     * @return 검색 조건에 맞는 CourseEntity 리스트
     */
    @Query("SELECT c FROM CourseEntity c WHERE " +
            "(:query IS NULL OR LOWER(c.courseName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.courseCode) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:department IS NULL OR c.specialized_major = :department) AND " +
            "(:grade IS NULL OR c.grade = :grade)")
    List<CourseEntity> searchCourses(@Param("query") String query,
                                     @Param("department") String department,
                                     @Param("grade") String grade);


    // LectureFilterController에서 사용하던 기능을 대체
    @Query("SELECT c.courseCode FROM CourseEntity c WHERE c.courseCode LIKE CONCAT(:prefix, '%')")
    List<String> findCourseCodesStartingWith(@Param("prefix") String prefix);
}