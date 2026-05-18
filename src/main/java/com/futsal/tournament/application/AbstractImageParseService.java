package com.futsal.tournament.application;

import com.futsal.shared.infrastructure.ModalParserClient;
import com.futsal.shared.infrastructure.ModalParserClient.ParseType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.presentation.dto.ParsedMatchDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 대진표/시간일정표 이미지 파싱 서비스 공통 로직.
 *
 * <p>Modal 호출, 팀명→ID 매핑, 변환 유틸을 공유하고
 * 하위 클래스가 ParseType과 응답 조립만 담당한다.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractImageParseService {

  private static final DateTimeFormatter DT_FORMATTER =
      DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  protected final ModalParserClient modalParserClient;
  protected final TournamentRepository tournamentRepository;
  protected final TournamentParticipantRepository participantRepository;

  // ── 하위 클래스 구현 ───────────────────────────────────────────────────────

  protected abstract ParseType parseType();

  // ── 공통 ──────────────────────────────────────────────────────────────────

  protected List<Map<String, Object>> requestParse(
      Long tournamentId, MultipartFile image
  ) {
    Tournament tournament = tournamentRepository.findById(tournamentId)
        .orElseThrow(() -> new RuntimeException(
            "대회를 찾을 수 없습니다: " + tournamentId));

    List<String> teamNames = participantRepository
        .findByTournamentIdAndConfirmed(tournamentId)
        .stream()
        .map(TournamentParticipant::getTeamName)
        .filter(name -> name != null && !name.isBlank())
        .toList();

    log.info("[{}] 대회={}, 파일={}, 참가팀={}개",
        parseType(), tournamentId, image.getOriginalFilename(), teamNames.size());

    return modalParserClient.requestParse(
        toBytes(image),
        parseType(),
        tournament.getTournamentDate().toString(),
        teamNames
    );
  }

  protected Map<String, Long> buildParticipantNameMap(Long tournamentId) {
    return participantRepository
        .findByTournamentIdAndConfirmed(tournamentId)
        .stream()
        .filter(p -> p.getTeamName() != null)
        .collect(Collectors.toMap(
            p -> normalize(p.getTeamName()),
            TournamentParticipant::getTeamId,
            (a, b) -> a
        ));
  }

  /** Modal 응답 Map → ParsedMatchDto */
  protected ParsedMatchDto toDto(
      Map<String, Object> raw, Map<String, Long> nameToId, Set<String> unmapped
  ) {
    String team1Name = str(raw.get("team1Name"));
    String team2Name = str(raw.get("team2Name"));

    Long team1Id = team1Name != null ? nameToId.get(normalize(team1Name)) : null;
    Long team2Id = team2Name != null ? nameToId.get(normalize(team2Name)) : null;

    if (team1Id == null && team1Name != null) unmapped.add(team1Name);
    if (team2Id == null && team2Name != null) unmapped.add(team2Name);

    return ParsedMatchDto.builder()
        .round(toInt(raw.get("round")))
        .matchNumber(toInt(raw.get("matchNumber")))
        .groupId(str(raw.get("groupId")))
        .team1Name(team1Name)
        .team2Name(team2Name)
        .team1Id(team1Id)
        .team2Id(team2Id)
        .scheduledAt(str(raw.get("scheduledAt")))
        .venueName(str(raw.get("venueName")))
        .hasUnmappedTeam(team1Id == null || team2Id == null)
        .build();
  }

  protected List<ParsedMatchDto> toDtoList(
      List<Map<String, Object>> rawList,
      Map<String, Long> nameToId,
      Set<String> unmapped
  ) {
    List<ParsedMatchDto> result = new ArrayList<>();
    for (Map<String, Object> raw : rawList) {
      result.add(toDto(raw, nameToId, unmapped));
    }
    return result;
  }

  public void warmup() {
    modalParserClient.warmup();
  }

  // ── 변환 유틸 ─────────────────────────────────────────────────────────────

  private byte[] toBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException("이미지 파일을 읽을 수 없습니다.", e);
    }
  }

  protected String normalize(String name) {
    if (name == null) return "";
    return name.replaceAll("\\s+", "").toLowerCase();
  }

  protected String str(Object val) {
    if (val == null || "null".equals(String.valueOf(val))) return null;
    return val.toString().trim();
  }

  protected Integer toInt(Object val) {
    if (val == null) return null;
    try {
      return Integer.parseInt(val.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  protected LocalDateTime toDateTime(Object val) {
    if (val == null || "null".equals(String.valueOf(val))) return null;
    try {
      return LocalDateTime.parse(val.toString(), DT_FORMATTER);
    } catch (DateTimeParseException e) {
      log.debug("scheduledAt 파싱 실패 (무시): {}", val);
      return null;
    }
  }
}
