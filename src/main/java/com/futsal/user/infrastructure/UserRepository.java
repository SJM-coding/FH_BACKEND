package com.futsal.user.infrastructure;

import com.futsal.user.domain.User;
import com.futsal.user.domain.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User 리포지토리
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    boolean existsByKakaoId(Long kakaoId);

    /**
     * 인증 상태별 사용자 조회
     */
    List<User> findByVerificationStatus(VerificationStatus status);

    /**
     * 인증 대기 중인 사용자 수
     */
    long countByVerificationStatus(VerificationStatus status);
}
