package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCourseSelectionRepository extends JpaRepository<UserCourseSelectionEntity, String> { // ID 타입은 selectionId(String)
    Optional<UserCourseSelectionEntity> findByUser_UserId(String userId); // userId로 조회
}