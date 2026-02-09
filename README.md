# FUTSAL HUB 🏟️
아마추어 풋살 대회 모음 사이트

---

## 📋 Phase 2-1 완료: 카카오 로그인 + 사용자 관리

### ✅ 구현된 기능
- 카카오 OAuth2 소셜 로그인
- JWT 기반 인증 (Access Token + Refresh Token)
- 사용자 정보 관리
- SOLID 원칙 준수 구현

### 🏗️ SOLID 원칙 적용

#### 1. SRP (Single Responsibility Principle)
- `JwtTokenProvider`: JWT 생성/검증만 담당
- `CustomOAuth2UserService`: OAuth2 사용자 로드만 담당
- `OAuth2SuccessHandler`: 로그인 성공 처리만 담당
- `JwtAuthenticationFilter`: JWT 인증만 담당

#### 2. OCP (Open-Closed Principle)
- `OAuth2UserInfo` 인터페이스로 새 OAuth2 제공자 추가 가능
- `getOAuth2UserInfo()` 메서드에서 제공자별 분기 처리

#### 3. LSP (Liskov Substitution Principle)
- `KakaoOAuth2UserInfo`가 `OAuth2UserInfo` 인터페이스를 올바르게 구현

#### 4. ISP (Interface Segregation Principle)
- `OAuth2UserInfo`: 작고 구체적인 인터페이스

#### 5. DIP (Dependency Inversion Principle)
- `CustomOAuth2UserService`가 `OAuth2UserInfo` 추상화에 의존
- 구체 클래스가 아닌 인터페이스에 의존

---

## 🔧 백엔드 설정

### 1. 카카오 개발자 설정
1. [Kakao Developers](https://developers.kakao.com/) 접속
2. 애플리케이션 생성
3. **REST API 키** 복사 → `KAKAO_CLIENT_ID`
4. **보안 탭** → Client Secret 생성 → `KAKAO_CLIENT_SECRET`
5. **플랫폼 설정** → Web 플랫폼 추가
   - 사이트 도메인: `http://localhost:8080`
6. **Redirect URI 설정**
   - `http://localhost:8080/login/oauth2/code/kakao`

### 2. 환경변수 설정 (.env)
```bash
# 카카오 OAuth2
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=your-kakao-client-secret

# JWT Secret (256비트 이상)
JWT_SECRET=your-secret-key-minimum-256-bits-long-for-HS256-algorithm
```

### 3. 실행
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 📡 API 엔드포인트

### 인증 관련
| Method | URL | 설명 | 인증 필요 |
|--------|-----|------|----------|
| GET | `/api/auth/me` | 현재 로그인 사용자 정보 | ✅ |
| POST | `/api/auth/refresh` | Access Token 갱신 | ❌ |

### OAuth2
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/oauth2/authorization/kakao` | 카카오 로그인 시작 |
| GET | `/login/oauth2/code/kakao` | 카카오 콜백 (자동) |

### 대회 관련
| Method | URL | 설명 | 인증 필요 |
|--------|-----|------|----------|
| GET | `/api/tournaments` | 전체 대회 목록 | ❌ |
| GET | `/api/tournaments/{id}` | 단일 대회 조회 | ❌ |
| GET | `/api/tournaments/region/{region}` | 지역별 대회 | ❌ |
| GET | `/api/tournaments/search?keyword={}` | 키워드 검색 | ❌ |
| POST | `/api/tournaments` | 대회 등록 | ✅ (예정) |
| POST | `/api/tournaments/poster` | 포스터 업로드 | ✅ (예정) |
| DELETE | `/api/tournaments/{id}` | 대회 삭제 | ✅ (예정) |

---

## 🌐 프론트엔드 통합 (다음 단계)

### 로그인 플로우
```
1. 사용자가 "카카오 로그인" 버튼 클릭
2. → GET http://localhost:8080/oauth2/authorization/kakao
3. → 카카오 로그인 페이지로 리다이렉트
4. → 사용자 로그인 완료
5. → 백엔드 콜백 처리
6. → 프론트엔드로 리다이렉트 (토큰 포함)
   http://localhost:3000/oauth2/callback?accessToken=...&refreshToken=...
7. 프론트엔드에서 토큰 저장 (localStorage)
8. 이후 API 요청 시 Authorization 헤더에 토큰 포함
   Authorization: Bearer {accessToken}
```

---

## 📂 프로젝트 구조

```
backend/
├── src/main/java/com/futsal/
│   ├── auth/                      # 인증 관련 (새로 추가)
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── domain/
│   │   │   ├── OAuth2UserInfo.java        (인터페이스)
│   │   │   └── KakaoOAuth2UserInfo.java   (구현체)
│   │   ├── filter/
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── handler/
│   │   │   └── OAuth2SuccessHandler.java
│   │   ├── jwt/
│   │   │   └── JwtTokenProvider.java
│   │   └── service/
│   │       └── CustomOAuth2UserService.java
│   ├── config/
│   │   ├── SecurityConfig.java            (새로 추가)
│   │   ├── CorsConfig.java
│   │   ├── CorsProperties.java
│   │   ├── S3Config.java
│   │   └── S3Properties.java
│   ├── controller/
│   │   └── TournamentController.java
│   ├── dto/
│   ├── entity/
│   │   ├── User.java                      (새로 추가)
│   │   ├── UserRole.java                  (새로 추가)
│   │   └── Tournament.java                (user_id 추가)
│   ├── repository/
│   │   ├── UserRepository.java            (새로 추가)
│   │   └── TournamentRepository.java
│   └── service/
```

---

## 🚀 다음 단계

### Phase 2-2: 대회 상태 관리
- [ ] 대회 상태 (OPEN, CLOSING_SOON, CLOSED)
- [ ] 현재 참가자 수 관리
- [ ] 마감 처리 기능
- [ ] 마감 임박 자동 업데이트

### Phase 2-3: 내가 등록한 대회 관리
- [ ] 사용자별 대회 목록 조회
- [ ] 대회 수정 권한 체크
- [ ] 대회 상태 변경 API

---

## ⚙️ 환경 설정

### 로컬 개발 (H2)
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 프로덕션 (MySQL + AWS)
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## 📝 라이센스
© 2026 FUTSAL HUB
