package com.futsal.shared.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal 서버리스 AI 파서 HTTP 클라이언트.
 * S3Service와 같은 shared infrastructure.
 */
@Slf4j
@Component
public class ModalParserClient {

  /** Modal parse endpoint에 전달하는 파싱 유형 */
  public enum ParseType {
    /** 대진표 — 팀 매칭(round, groupId, team1/2Name) 추출 */
    BRACKET,
    /** 시간일정표 — 경기 일정(scheduledAt, venueName) 추출 */
    SCHEDULE
  }

  private final RestClient restClient;

  @Value("${modal.parser.parse-url}")
  private String parseUrl;

  @Value("${modal.parser.warmup-url}")
  private String warmupUrl;

  public ModalParserClient(RestClient.Builder builder) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
    factory.setReadTimeout((int) Duration.ofMinutes(10).toMillis());

    this.restClient = builder.requestFactory(factory).build();
  }

  /**
   * 이미지를 Modal AI 서버로 전송해 파싱된 경기 목록을 받는다.
   *
   * @param imageBytes     이미지 바이트 배열
   * @param parseType      BRACKET(대진표) 또는 SCHEDULE(시간일정표)
   * @param tournamentDate 대회 날짜 — SCHEDULE 파싱 시 시간 컨텍스트 제공
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> requestParse(
      byte[] imageBytes, ParseType parseType,
      String tournamentDate, List<String> teamNames
  ) {
    Map<String, Object> body = new HashMap<>();
    body.put("imageBase64", Base64.getEncoder().encodeToString(imageBytes));
    body.put("parseType", parseType.name());
    body.put("tournamentDate", tournamentDate);
    body.put("teamNames", teamNames);

    try {
      Map<?, ?> response = restClient.post()
          .uri(parseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);

      if (response == null) {
        log.warn("Modal 응답이 null입니다.");
        return Collections.emptyList();
      }

      log.info("Modal 응답 success: {}, count: {}, error: {}",
          response.get("success"), response.get("count"), response.get("error"));
      log.info("Modal 응답 raw: {}", response.get("raw"));

      if (!response.containsKey("matches")) {
        log.warn("Modal 응답에 matches 필드가 없습니다.");
        return Collections.emptyList();
      }

      return (List<Map<String, Object>>) response.get("matches");

    } catch (Exception e) {
      log.error("Modal 파서 호출 실패: {}", e.getMessage());
      throw new RuntimeException("AI 파서 호출에 실패했습니다.", e);
    }
  }

  /** Modal 컨테이너 pre-warm ping. 실패해도 무시한다. */
  public void warmup() {
    try {
      restClient.get().uri(warmupUrl).retrieve().toBodilessEntity();
      log.debug("Modal 컨테이너 워밍 완료");
    } catch (Exception e) {
      log.debug("Modal 워밍 요청 실패 (무시): {}", e.getMessage());
    }
  }
}
