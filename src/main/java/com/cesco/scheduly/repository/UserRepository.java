package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> { // ID 타입은 userId(String)
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    boolean existsByStudentId(String studentId);
    Optional<User> findByStudentId(String studentId);
}