package com.futsal.common.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.futsal.config.S3Properties;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3Service(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 포스터 이미지를 S3에 업로드하고 URL을 반환
     */
    public String uploadPoster(MultipartFile file) {
        return uploadFile(file, "posters/");
    }

    /**
     * 프로필 이미지를 S3에 업로드하고 URL을 반환
     */
    public String uploadProfileImage(MultipartFile file) {
        return uploadFile(file, "profiles/");
    }

    /**
     * 대진표 이미지를 S3에 업로드하고 URL을 반환
     */
    public String uploadBracketImage(MultipartFile file) {
        return uploadFile(file, "brackets/");
    }

    /**
     * 파일을 S3에 업로드하고 URL을 반환
     */
    private String uploadFile(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }

        String originalName = file.getOriginalFilename();
        String safeName = (originalName == null || originalName.isBlank())
                ? "file"
                : originalName.replaceAll("[\\\\/]+", "_");
        String fileName = prefix + UUID.randomUUID() + "_" + safeName;
        String bucketName = properties.getS3().getBucket();

        try {
            // ACL 대신 버킷 정책으로 퍼블릭 접근 관리
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return buildPublicUrl(fileName);

        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패: 파일 읽기 오류", e);
        } catch (Exception e) {
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
        }
    }

    private String buildPublicUrl(String key) {
        String publicUrl = properties.getS3().getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "") + "/" + key;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                properties.getS3().getBucket(),
                properties.getRegion(),
                key);
    }
}
