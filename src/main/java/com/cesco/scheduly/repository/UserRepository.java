package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> { // ID 타입은 userId(String)
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);

    boolean existsByStudentId(String studentId);
    Optional<UserEntity> findByStudentId(String studentId);
}