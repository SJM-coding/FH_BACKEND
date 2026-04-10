package com.futsal.tournament.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * 공유 코드 Value Object
 *
 * - participantCode: 참가팀이 대회에 참가 신청할 때 사용 (대회 생성 시 발급)
 * - staffCode:       운영진이 점수 입력 페이지에 접근할 때 사용 (대회 확정 시 발급)
 *
 * JPA @Embeddable 제약으로 final 필드를 쓸 수 없으나,
 * 외부에 setter를 노출하지 않아 사실상(effectively) 불변 객체로 동작한다.
 * 상태 변화가 필요할 때는 새 인스턴스를 반환한다.
 */
@Embeddable
public class ShareCode {

  private static final String CODE_CHARS =
      "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 6;

  @Column(name = "participant_code", length = 8, unique = true)
  private String participantCode;

  @Column(name = "staff_code", length = 8, unique = true)
  private String staffCode;

  /** JPA 전용 — 외부에서 직접 호출 금지 */
  protected ShareCode() {}

  private ShareCode(String participantCode, String staffCode) {
    this.participantCode = participantCode;
    this.staffCode = staffCode;
  }

  // ── 팩토리 메서드 ──────────────────────────────────────────────────

  /**
   * 참가 코드만 발급 (대회 생성 시 호출)
   *
   * @param isUnique 고유성 검증 술어 — Application Service가 Repository를
   *                 이용해 주입 (예: code -> !repo.existsByParticipantCode(code))
   */
  public static ShareCode generateParticipantCode(Predicate<String> isUnique) {
    return new ShareCode(generateUniqueCode(isUnique), null);
  }

  // ── 상태 변화 메서드 (새 인스턴스 반환) ───────────────────────────

  /**
   * 운영진 코드를 추가 발급한다.
   * 이미 staffCode가 있으면 동일한 인스턴스를 반환한다.
   *
   * @param isUnique 고유성 검증 술어
   */
  public ShareCode issueStaffCode(Predicate<String> isUnique) {
    if (this.staffCode != null) {
      return this;
    }
    return new ShareCode(this.participantCode, generateUniqueCode(isUnique));
  }

  // ── 읽기 전용 접근자 ──────────────────────────────────────────────

  public String getParticipantCode() {
    return participantCode;
  }

  public String getStaffCode() {
    return staffCode;
  }

  // ── 내부 유틸리티 ─────────────────────────────────────────────────

  private static String generateUniqueCode(Predicate<String> isUnique) {
    SecureRandom random = new SecureRandom();
    String code;
    do {
      StringBuilder sb = new StringBuilder(CODE_LENGTH);
      for (int i = 0; i < CODE_LENGTH; i++) {
        sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
      }
      code = sb.toString();
    } while (!isUnique.test(code));
    return code;
  }
}
