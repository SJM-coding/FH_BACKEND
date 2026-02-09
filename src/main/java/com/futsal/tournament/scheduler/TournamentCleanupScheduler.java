package com.futsal.tournament.scheduler;

import com.futsal.tournament.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Phase 2-5: 날짜 지난 대회 자동 삭제 스케줄러
 * 매일 새벽 3시에 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentCleanupScheduler {

    private final TournamentService tournamentService;

    /**
     * 매일 새벽 3시에 만료된 대회 자동 삭제
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredTournaments() {
        log.info("Starting expired tournaments cleanup...");
        
        int deletedCount = tournamentService.deleteExpiredTournaments();
        
        log.info("Expired tournaments cleanup completed. Deleted {} tournaments.", deletedCount);
    }
}
