package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, String> { // ID 타입은 preferenceId(String)
    Optional<UserPreferenceEntity> findByUser_UserId(String userId); // userId로 조회
}