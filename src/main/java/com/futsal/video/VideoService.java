package com.futsal.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jakarta.annotation.PostConstruct;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class VideoService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.sheets.id:}")
    private String googleSheetId;

    private static final String REDIS_KEY = "featured:videos";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // 기본 채널 목록 (Google Sheets 실패 시 fallback)
    private static final List<ChannelInfo> DEFAULT_CHANNELS = List.of(
        new ChannelInfo("UCrEsO4yYYCIvqRvPjHeNodQ", "슛포러브"),
        new ChannelInfo("UC1eqIs-FliQzLZW1_1DPWDQ", "풋살러 마르코"),
        new ChannelInfo("UC2AKksZvQPH8XLIwgLiNhJQ", "쌈바풋살")
    );

    public VideoService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 앱 시작 시 캐시 워밍업 (첫 요청 지연 방지)
     */
    @PostConstruct
    public void warmUpCache() {
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 앱 완전히 뜬 후 실행
                String cached = redisTemplate.opsForValue().get(REDIS_KEY);
                if (cached == null) {
                    log.info("Warming up video cache...");
                    List<VideoResponse> videos = fetchAllVideos();
                    if (!videos.isEmpty()) {
                        String json = objectMapper.writeValueAsString(videos);
                        redisTemplate.opsForValue().set(REDIS_KEY, json, CACHE_TTL);
                        log.info("Video cache warmed up with {} videos", videos.size());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to warm up video cache", e);
            }
        }, "video-cache-warmup").start();
    }

    public List<VideoResponse> getFeaturedVideos(int limit) {
        try {
            // Redis에서 캐시 확인
            String cached = redisTemplate.opsForValue().get(REDIS_KEY);
            List<VideoResponse> allVideos;

            if (cached != null) {
                // 캐시 있으면 사용
                allVideos = objectMapper.readValue(cached, new TypeReference<List<VideoResponse>>() {});
                log.debug("Loaded {} videos from Redis cache", allVideos.size());
            } else {
                // 캐시 없으면 YouTube에서 fetch
                allVideos = fetchAllVideos();

                // Redis에 저장
                if (!allVideos.isEmpty()) {
                    String json = objectMapper.writeValueAsString(allVideos);
                    redisTemplate.opsForValue().set(REDIS_KEY, json, CACHE_TTL);
                    log.info("Cached {} videos to Redis", allVideos.size());
                }
            }

            // 셔플해서 반환
            List<VideoResponse> shuffled = new ArrayList<>(allVideos);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());

            if (shuffled.size() > limit) {
                return shuffled.subList(0, limit);
            }
            return shuffled;

        } catch (Exception e) {
            log.error("Failed to get featured videos", e);
            return Collections.emptyList();
        }
    }

    private List<VideoResponse> fetchAllVideos() {
        List<ChannelInfo> channels = getChannels();
        List<VideoResponse> allVideos = new ArrayList<>();

        for (int i = 0; i < channels.size(); i++) {
            ChannelInfo channel = channels.get(i);
            try {
                List<VideoResponse> videos = fetchVideosFromChannel(channel);
                allVideos.addAll(videos);
                // 요청 사이 딜레이 (rate limiting 방지)
                if (i < channels.size() - 1) {
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch videos from channel: {}", channel.name(), e);
            }
        }

        return allVideos;
    }

    private List<ChannelInfo> getChannels() {
        // Google Sheets ID가 없으면 기본 채널 사용
        if (googleSheetId == null || googleSheetId.isBlank()) {
            return DEFAULT_CHANNELS;
        }

        try {
            return fetchChannelsFromGoogleSheets();
        } catch (Exception e) {
            log.error("Failed to fetch channels from Google Sheets, using default", e);
            return DEFAULT_CHANNELS;
        }
    }

    private List<ChannelInfo> fetchChannelsFromGoogleSheets() throws Exception {
        String csvUrl = "https://docs.google.com/spreadsheets/d/" + googleSheetId + "/export?format=csv";

        URL url = new URL(csvUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        List<ChannelInfo> channels = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                // 첫 줄(헤더) 스킵
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String channelId = parts[0].trim();
                    String channelName = parts[1].trim();

                    if (!channelId.isEmpty() && !channelName.isEmpty()) {
                        channels.add(new ChannelInfo(channelId, channelName));
                    }
                }
            }
        }

        log.info("Loaded {} channels from Google Sheets", channels.size());
        return channels.isEmpty() ? DEFAULT_CHANNELS : channels;
    }

    private List<VideoResponse> fetchVideosFromChannel(ChannelInfo channel) throws Exception {
        // RSS 피드 URL
        String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channel.id();
        log.info("Fetching RSS from: {} (channel: {})", rssUrl, channel.name());

        // 재시도 로직 (최대 2회)
        List<VideoResponse> videos = null;
        Exception lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                videos = parseRssFeed(rssUrl, channel.name());
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed for channel: {}", attempt + 1, channel.name());
                if (attempt < 1) {
                    Thread.sleep(1000); // 재시도 전 대기
                }
            }
        }

        if (videos == null) {
            throw lastException;
        }

        return videos;
    }

    private List<VideoResponse> parseRssFeed(String rssUrl, String channelName) throws Exception {
        List<VideoResponse> videos = new ArrayList<>();

        URL url = new URL(rssUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/xml, text/xml, */*");
        conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try (InputStream is = conn.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            NodeList entries = doc.getElementsByTagName("entry");

            // 최대 5개만 가져옴
            int count = Math.min(entries.getLength(), 5);

            for (int i = 0; i < count; i++) {
                Element entry = (Element) entries.item(i);

                String videoId = getTagValue(entry, "yt:videoId");
                String title = getTagValue(entry, "title");
                String published = getTagValue(entry, "published");

                // 썸네일 URL (고화질)
                String thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";

                videos.add(VideoResponse.builder()
                    .videoId(videoId)
                    .title(title)
                    .channelName(channelName)
                    .thumbnailUrl(thumbnailUrl)
                    .publishedAt(published != null ? published.substring(0, 10) : "")
                    .build());
            }
        }

        return videos;
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return "";
    }

    // 채널 정보
    private record ChannelInfo(String id, String name) {}
}
