package com.futsal.video;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class VideoService {

    // 캐시 (1시간 유지)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000; // 1시간

    // 풋살 유튜브 채널 목록 (채널ID, 채널명)
    private static final List<ChannelInfo> CHANNELS = List.of(
        new ChannelInfo("UCrEsO4yYYCIvqRvPjHeNodQ", "슛포러브"),
        new ChannelInfo("UCY8P3C3ktpzddpaUJvJzgKw", "풋살러 마르코"),
        new ChannelInfo("UCmJA9io7JByYWmGM_ZvLhmw", "더풋살")
    );

    public List<VideoResponse> getFeaturedVideos(int limit) {
        List<VideoResponse> allVideos = new ArrayList<>();

        for (ChannelInfo channel : CHANNELS) {
            try {
                List<VideoResponse> videos = fetchVideosFromChannel(channel);
                allVideos.addAll(videos);
            } catch (Exception e) {
                log.warn("Failed to fetch videos from channel: {}", channel.name(), e);
            }
        }

        // 날짜순 정렬 후 limit개 반환
        allVideos.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));

        if (allVideos.size() > limit) {
            return allVideos.subList(0, limit);
        }
        return allVideos;
    }

    private List<VideoResponse> fetchVideosFromChannel(ChannelInfo channel) throws Exception {
        // 캐시 확인
        CacheEntry cached = cache.get(channel.id());
        if (cached != null && !cached.isExpired()) {
            return cached.videos();
        }

        // RSS 피드 URL
        String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channel.id();

        List<VideoResponse> videos = parseRssFeed(rssUrl, channel.name());

        // 캐시 저장
        cache.put(channel.id(), new CacheEntry(videos, System.currentTimeMillis()));

        return videos;
    }

    private List<VideoResponse> parseRssFeed(String rssUrl, String channelName) throws Exception {
        List<VideoResponse> videos = new ArrayList<>();

        URL url = new URL(rssUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

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

    // 캐시 엔트리
    private record CacheEntry(List<VideoResponse> videos, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }

    // 채널 정보
    private record ChannelInfo(String id, String name) {}
}
