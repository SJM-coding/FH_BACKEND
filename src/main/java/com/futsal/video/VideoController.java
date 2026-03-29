package com.futsal.video;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping("/featured")
    public ResponseEntity<List<VideoResponse>> getFeaturedVideos(
            @RequestParam(defaultValue = "6") int limit
    ) {
        List<VideoResponse> videos = videoService.getFeaturedVideos(limit);
        return ResponseEntity.ok(videos);
    }
}
