package com.futsal.tournament.application;

import com.futsal.shared.infrastructure.ModalParserClient;
import com.futsal.shared.infrastructure.ModalParserClient.ParseType;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.presentation.dto.ParsedMatchDto;
import com.futsal.tournament.presentation.dto.ScheduleParseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시간일정표 이미지를 AI로 파싱해 경기 일정 미리보기를 반환하는 서비스.
 * 운영자가 결과를 확인하면 BracketCommandService.updateMatchSchedules()로 저장한다.
 */
@Service
public class ScheduleImageParseService extends AbstractImageParseService {

  public ScheduleImageParseService(
      ModalParserClient modalParserClient,
      TournamentRepository tournamentRepository,
      TournamentParticipantRepository participantRepository
  ) {
    super(modalParserClient, tournamentRepository, participantRepository);
  }

  @Override
  protected ParseType parseType() {
    return ParseType.SCHEDULE;
  }

  @Transactional(readOnly = true)
  public ScheduleParseResponse parse(Long tournamentId, MultipartFile image) {
    List<Map<String, Object>> rawMatches = requestParse(tournamentId, image);

    // 시간일정표는 팀명이 없으므로 nameToId 매핑 불필요
    List<ParsedMatchDto> matches = toDtoList(
        rawMatches, Collections.emptyMap(), new HashSet<>()
    );

    return ScheduleParseResponse.builder()
        .matches(matches)
        .unmappedTeamNames(new ArrayList<>())
        .totalCount(matches.size())
        .hasWarning(false)
        .build();
  }
}
