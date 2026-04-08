package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TournamentPageResponse {
  private List<TournamentListResponse> content;
  private boolean hasNext;
  private long totalElements;
}
