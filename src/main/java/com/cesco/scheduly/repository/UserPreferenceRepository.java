package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.entity.User; // User 엔티티 사용
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, String> { // PK 타입을 String(UUID) 또는 Long으로 결정
    Optional<UserPreferenceEntity> findByUser(User user);
    Optional<UserPreferenceEntity> findByUserId(Long userId); // User의 ID(Long)로 조회
}