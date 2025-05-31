package com.cesco.scheduly.repository;

import com.cesco.scheduly.entity.User; // 팀원 User 엔티티 사용
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> { // ID 타입을 Long으로 변경

    Optional<User> findByStudentId(String studentId); // studentId로 사용자 조회
    boolean existsByStudentId(String studentId);   // studentId 중복 확인

    // Optional<User> findByName(String name); // 이름은 고유하지 않을 수 있으므로, 필요시 다른 방식으로 처리
    // boolean existsByName(String name);
}