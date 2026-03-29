package com.futsal.video;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VideoResponse {
    private String videoId;
    private String title;
    private String channelName;
    private String thumbnailUrl;
    private String publishedAt;
}
