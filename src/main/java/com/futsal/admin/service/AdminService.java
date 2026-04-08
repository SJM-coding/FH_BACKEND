package com.futsal.admin.service;

import com.futsal.user.domain.User;
import com.futsal.user.domain.VerificationStatus;
import com.futsal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<User> getPendingVerifications() {
    return userRepository.findByVerificationStatus(VerificationStatus.PENDING);
  }

  @Transactional(readOnly = true)
  public List<User> getUsers(String verificationStatus) {
    if (verificationStatus != null) {
      VerificationStatus status =
          VerificationStatus.valueOf(verificationStatus.toUpperCase());
      return userRepository.findByVerificationStatus(status);
    }
    return userRepository.findAll();
  }

  @Transactional
  public User approveVerification(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
    user.approveVerification();
    return userRepository.save(user);
  }

  @Transactional
  public User rejectVerification(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
    user.rejectVerification();
    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getStats() {
    return Map.of(
        "totalUsers", userRepository.count(),
        "pendingVerifications",
            userRepository.countByVerificationStatus(VerificationStatus.PENDING),
        "verifiedUsers",
            userRepository.countByVerificationStatus(VerificationStatus.VERIFIED)
    );
  }
}
