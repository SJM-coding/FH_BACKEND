package com.futsal.tournament.service;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.repository.TournamentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
public class RedisTournamentViewCountService implements TournamentViewCountService {

    private static final String PENDING_KEY_PREFIX = "tournament:view:pending:";
    private static final String DIRTY_SET_KEY = "tournament:view:dirty";

    private final StringRedisTemplate redisTemplate;
    private final TournamentRepository tournamentRepository;

    public RedisTournamentViewCountService(
            StringRedisTemplate redisTemplate,
            TournamentRepository tournamentRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public int recordViewAndGetVisibleCount(Tournament tournament) {
        String tournamentId = tournament.getId().toString();
        String pendingKey = buildPendingKey(tournamentId);

        Long pendingCount = redisTemplate.opsForValue().increment(pendingKey);
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, tournamentId);

        long visibleCount = (long) tournament.getViewCount() + (pendingCount != null ? pendingCount : 0L);
        return Math.toIntExact(visibleCount);
    }

    @Scheduled(fixedDelayString = "${app.view-count.redis.flush-interval-ms:5000}")
    @Transactional
    public void flushPendingViewCounts() {
        Set<String> dirtyTournamentIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
        if (dirtyTournamentIds == null || dirtyTournamentIds.isEmpty()) {
            return;
        }

        for (String tournamentId : dirtyTournamentIds) {
            flushTournamentViewCount(tournamentId);
        }
    }

    private void flushTournamentViewCount(String tournamentId) {
        String pendingKey = buildPendingKey(tournamentId);
        String pendingValue = redisTemplate.opsForValue().getAndDelete(pendingKey);

        if (pendingValue != null) {
            long increment = Long.parseLong(pendingValue);
            if (increment > 0) {
                tournamentRepository.incrementViewCountBy(Long.parseLong(tournamentId), increment);
                log.info("Flushed pending tournament views: tournamentId={}, count={}", tournamentId, increment);
            }
        }

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey))) {
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, tournamentId);
        }
    }

    private String buildPendingKey(String tournamentId) {
        return PENDING_KEY_PREFIX + tournamentId;
    }
}
