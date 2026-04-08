package com.futsal.video.presentation;

import com.futsal.video.application.VideoService;
import com.futsal.video.dto.VideoResponse;
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
    return ResponseEntity.ok(videoService.getFeaturedVideos(limit));
  }
}
