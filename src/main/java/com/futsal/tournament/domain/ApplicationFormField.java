//package com.futsal.tournament.domain;
//
//import jakarta.persistence.*;
//import lombok.*;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * 대회 신청 양식 필드 (Value Object)
// *
// * 대회별로 요구하는 추가 정보를 정의
// * 예: 팀 평균 연령, 팀 수준, 참가 목적 등
// */
//@Embeddable
//@Getter
//@NoArgsConstructor(access = AccessLevel.PROTECTED)
//@AllArgsConstructor
//@Builder
//public class ApplicationFormField {
//
//    /**
//     * 필드명
//     */
//    @Column(name = "field_name", length = 100)
//    private String fieldName;
//
//    /**
//     * 필드 라벨 (화면 표시용)
//     */
//    @Column(name = "field_label", length = 200)
//    private String fieldLabel;
//
//    /**
//     * 필드 타입
//     */
//    @Enumerated(EnumType.STRING)
//    @Column(name = "field_type", length = 20)
//    private FieldType fieldType;
//
//    /**
//     * 필수 여부
//     */
//    @Column(name = "is_required")
//    private boolean required;
//
//    /**
//     * 선택지 (SELECT, RADIO인 경우)
//     */
//    @Column(name = "field_options", length = 1000)
//    private String options; // 쉼표로 구분된 옵션들
//
//    /**
//     * 도움말 텍스트
//     */
//    @Column(name = "field_placeholder", length = 500)
//    private String placeholder;
//
//    public enum FieldType {
//        TEXT("단답형"),
//        TEXTAREA("장문형"),
//        NUMBER("숫자"),
//        SELECT("선택"),
//        RADIO("라디오"),
//        CHECKBOX("체크박스");
//
//        private final String description;
//
//        FieldType(String description) {
//            this.description = description;
//        }
//
//        public String getDescription() {
//            return description;
//        }
//    }
//
//    /**
//     * 옵션 리스트 반환
//     */
//    public List<String> getOptionList() {
//        if (options == null || options.isEmpty()) {
//            return new ArrayList<>();
//        }
//        return List.of(options.split(","));
//    }
//
//    /**
//     * 값 검증
//     */
//    public boolean isValid(String value) {
//        // 필수 필드 체크
//        if (required && (value == null || value.trim().isEmpty())) {
//            return false;
//        }
//
//        // 선택형 필드는 옵션에 포함되어야 함
//        if (fieldType == FieldType.SELECT || fieldType == FieldType.RADIO) {
//            if (value != null && !getOptionList().contains(value)) {
//                return false;
//            }
//        }
//
//        // 숫자형 필드는 숫자여야 함
//        if (fieldType == FieldType.NUMBER) {
//            if (value != null) {
//                try {
//                    Integer.parseInt(value);
//                } catch (NumberFormatException e) {
//                    return false;
//                }
//            }
//        }
//
//        return true;
//    }
//}
