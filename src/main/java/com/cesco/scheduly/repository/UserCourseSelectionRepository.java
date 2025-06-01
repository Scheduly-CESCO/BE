package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.User; // User 엔티티 사용
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCourseSelectionRepository extends JpaRepository<UserCourseSelectionEntity, String> { // PK 타입은 selectionId(String)
    Optional<UserCourseSelectionEntity> findByUser(User user);
    Optional<UserCourseSelectionEntity> findByUser_Id(Long userId); // User의 PK 'id'를 기준으로 조회
}

