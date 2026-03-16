package com.futsal.user.repository;

import com.futsal.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User 리포지토리
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByKakaoId(Long kakaoId);
    
    boolean existsByKakaoId(Long kakaoId);
}
