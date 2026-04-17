package com.futsal.tournament.application;

import com.futsal.shared.infrastructure.ModalParserClient;
import com.futsal.shared.infrastructure.ModalParserClient.ParseType;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.presentation.dto.GroupAssignmentDto;
import com.futsal.tournament.presentation.dto.GroupParseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 대진표 이미지를 AI로 파싱해 조별 팀 배정 미리보기를 반환하는 서비스.
 * 운영자가 결과를 확인하면 BracketGeneratorService가 TournamentGroup과 경기를 생성한다.
 */
@Service
public class BracketImageParseService extends AbstractImageParseService {

  private final BracketGeneratorService bracketGeneratorService;

  public BracketImageParseService(
      ModalParserClient modalParserClient,
      TournamentRepository tournamentRepository,
      TournamentParticipantRepository participantRepository,
      BracketGeneratorService bracketGeneratorService
  ) {
    super(modalParserClient, tournamentRepository, participantRepository);
    this.bracketGeneratorService = bracketGeneratorService;
  }

  @Override
  protected ParseType parseType() {
    return ParseType.BRACKET;
  }

  /**
   * 대진표 이미지를 AI로 파싱해 조별 팀 배정 미리보기를 반환한다.
   */
  @Transactional(readOnly = true)
  public GroupParseResponse parse(Long tournamentId, MultipartFile image) {
    List<Map<String, Object>> rawList = requestParse(tournamentId, image);
    Map<String, Long> nameToId = buildParticipantNameMap(tournamentId);
    Set<String> unmapped = new HashSet<>();

    List<GroupAssignmentDto> assignments = rawList.stream()
        .map(raw -> {
          String groupId = str(raw.get("groupId"));
          String teamName = str(raw.get("teamName"));
          Long teamId = teamName != null ? nameToId.get(normalize(teamName)) : null;

          if (teamId == null && teamName != null) {
            unmapped.add(teamName);
          }

          return GroupAssignmentDto.builder()
              .groupId(groupId)
              .teamName(teamName)
              .teamId(teamId)
              .unmapped(teamId == null)
              .build();
        })
        .collect(Collectors.toList());

    return GroupParseResponse.builder()
        .assignments(assignments)
        .unmappedTeamNames(new ArrayList<>(unmapped))
        .totalCount(assignments.size())
        .hasWarning(!unmapped.isEmpty())
        .build();
  }

  /**
   * 운영자가 확인한 조별 팀 배정을 저장하고 라운드로빈 경기를 생성한다.
   *
   * @param tournamentId 대회 ID
   * @param assignments  확정된 조별 팀 배정 목록 (teamId가 null인 항목은 제외됨)
   */
  @Transactional
  public void confirm(Long tournamentId, List<GroupAssignmentDto> assignments) {
    // teamId가 매핑된 항목만 조별로 그룹핑
    Map<String, List<Long>> teamIdsByGroup = assignments.stream()
        .filter(a -> a.getGroupId() != null && a.getTeamId() != null)
        .collect(Collectors.groupingBy(
            GroupAssignmentDto::getGroupId,
            Collectors.mapping(GroupAssignmentDto::getTeamId, Collectors.toList())
        ));

    if (teamIdsByGroup.isEmpty()) {
      throw new RuntimeException("매핑된 팀이 없습니다. 팀 이름을 확인해주세요.");
    }

    bracketGeneratorService.generateFromGroupAssignments(tournamentId, teamIdsByGroup);
  }
}
