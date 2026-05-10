# Codex OAuth 배포형 AI 추천 구현 계획

## 0. 결론

포트폴리오 발표용 최종 방향은 다음 구조로 잡는다.

```text
발표컴 브라우저
-> Oracle Cloud 등에 배포된 DABOYEO 사이트
-> Oracle Cloud Spring Boot API
-> 작업컴 AI Gateway 터널
-> 작업컴 내부 openai-oauth
-> Codex/ChatGPT OAuth 기반 AI 응답
```

핵심은 AI가 데이터베이스를 직접 보는 구조가 아니라는 점이다.

```text
Spring 서버가 DB에서 실제 상영 후보를 조회한다.
Spring 서버가 후보를 1차 점수화한다.
AI는 서버가 넘긴 후보 안에서만 재정렬하고 추천 이유를 만든다.
최종 응답은 다시 Spring 서버가 검증 가능한 형태로 반환한다.
```

이 구조는 기존 로컬 AI, LM Studio, Ollama, Codex OAuth gateway 모두 같은 추천 파이프라인으로 취급할 수 있다. 차이는 AI provider의 endpoint와 인증 방식뿐이다.

## 1. 현재 프로젝트 구조 요약

현재 레포는 배포 구조를 단순하게 가져갈 수 있는 형태다.

```text
frontend/
  바닐라 HTML, CSS, JS

backend/
  Spring Boot 3.5
  Java 21
  Spring Web REST API
  Spring JDBC
  TiDB/MySQL
  정적 frontend 파일을 backend/src/main/resources/static/ 아래에 미러링 가능
```

현재 추천 흐름의 주요 파일은 다음과 같다.

```text
frontend/src/js/pages/daboyeoAi.js
  사용자의 추천 조건을 모으고 requestRecommendations(payload)를 호출한다.

frontend/src/js/api/client.js
  /api/recommendations 등 Spring API를 호출한다.

backend/src/main/java/kr/daboyeo/backend/api/recommendation/RecommendationController.java
  /api/recommendations 요청을 받는다.

backend/src/main/java/kr/daboyeo/backend/service/recommendation/RecommendationService.java
  세션 생성, 취향 프로필 생성, DB 후보 조회, 점수 계산, AI 호출, fallback 추천을 처리한다.

backend/src/main/java/kr/daboyeo/backend/service/recommendation/LocalModelRecommendationClient.java
  OpenAI-compatible /chat/completions API를 호출해서 후보 재정렬과 추천 이유를 받는다.

backend/src/main/resources/application.yml
  DABOYEO_LM_STUDIO_BASE_URL, 추천 모델명, CORS, DB 연결 정보를 환경변수로 받는다.
```

현재 구조에서 이미 좋은 점은 이거다.

```text
AI 실패 시 서버 deterministic fallback 추천이 있다.
AI는 DB 전체가 아니라 서버가 고른 후보만 받는다.
AI 응답은 JSON schema 형태로 제한된다.
프론트는 /api/recommendations만 알면 된다.
```

따라서 Codex OAuth를 붙일 때도 프론트를 크게 바꿀 필요가 없다. 백엔드의 AI 호출 위치만 provider화하면 된다.

## 2. 우리가 정리한 요구사항

### 2.1 발표 조건

```text
사이트는 Oracle Cloud 같은 클라우드 서버에 배포한다.
발표 때는 배포된 URL로 시연한다.
OpenAI API 키 과금 구조는 피하고 싶다.
Codex/ChatGPT OAuth는 발표용 또는 개발자 데모용으로만 쓴다.
작업컴에 이미 Codex/openai-oauth 환경이 있다면 발표 시간 동안 AI gateway로 활용할 수 있다.
```

### 2.2 AI 추천 버튼에서 해야 하는 일

```text
사용자가 선택한 조건을 수집한다.
조건 예시: 누구랑 보는지, 오늘 기분, 피하고 싶은 요소, 포스터 취향, 지역, 날짜, 시간대, 인원

Spring 서버가 TiDB/MySQL에서 실제 상영 후보를 조회한다.
조건 예시: 현재 이후 상영, 지역, 날짜, 시간대, 좌석 여유, 가격, 예매 링크

Spring 서버가 후보를 1차 점수화한다.
조건 예시: 장르, 분위기, 선호 포스터, 피하고 싶은 요소, 좌석, 상영 시간

AI가 후보를 재정렬하고 추천 이유를 만든다.
AI는 "주어진 후보 안에서만" 선택한다.

Spring 서버가 최종 3개 추천을 반환한다.
```

### 2.3 AI가 필요한 이유

단순 키워드 검색은 사용자 발화를 잘못 해석할 수 있다.

```text
"나 우울해"
-> 사용자의 감정 상태
-> 위로, 기분전환, 가벼운 영화 추천이 자연스럽다.

"우울한 영화 추천해줘"
-> 콘텐츠 분위기 요청
-> 슬픈 영화, 먹먹한 영화 추천이 자연스럽다.
```

이 프로젝트에서 AI의 역할은 "DB 검색기"가 아니라 "맥락 분석과 추천 설명기"다.

```text
AI가 하는 일:
  감정 상태와 콘텐츠 요청 구분
  추천 전략 결정
  후보 재정렬
  사용자에게 보여줄 짧은 추천 이유 생성

서버 코드가 하는 일:
  실제 DB 조회
  상영 가능 여부 확인
  좌석, 가격, 시간, 지역 필터링
  점수 계산
  없는 영화나 없는 상영 정보 방지
```

## 3. API 구조는 로컬 AI와 Codex가 거의 같다

서버 입장에서 보면 로컬 AI와 Codex OAuth gateway는 둘 다 "외부 AI provider"다.

```text
기존 로컬 AI
Spring 서버
-> http://127.0.0.1:1234/v1/chat/completions
-> LM Studio 모델

Codex OAuth gateway
Spring 서버
-> https://<gateway-tunnel>/api/movie-analysis
-> 작업컴 gateway
-> http://127.0.0.1:10531/v1/chat/completions
-> openai-oauth
```

공통점은 이거다.

```text
Spring이 후보 목록과 사용자 조건을 보낸다.
AI가 JSON으로 선택 결과를 돌려준다.
Spring이 AI 결과를 검증하고 최종 응답으로 바꾼다.
실패하면 Spring fallback 추천으로 돌아간다.
```

차이는 이거다.

```text
로컬 AI는 OpenAI-compatible endpoint를 직접 호출해도 된다.
Codex OAuth는 raw /v1 endpoint를 외부에 그대로 열면 안 된다.
Codex OAuth는 반드시 전용 gateway 또는 최소 인증 wrapper로 감싼다.
```

## 4. 금지 구조

다음 구조는 발표용이라도 피한다.

```text
Oracle Spring 서버
-> https://<public-tunnel>/v1/chat/completions
-> 작업컴 openai-oauth raw endpoint
```

이 방식이 위험한 이유는 단순하다.

```text
openai-oauth는 계정 OAuth 토큰을 기반으로 동작한다.
raw /v1을 공개하면 해당 endpoint를 아는 사람이 임의 요청을 보낼 수 있다.
요청 범위가 영화 추천으로 제한되지 않는다.
토큰, 계정, 사용량, 로그 위험이 커진다.
```

따라서 외부 공개 endpoint는 반드시 DABOYEO 전용 API여야 한다.

```text
허용:
POST /api/movie-analysis

비허용:
POST /v1/chat/completions 를 그대로 공개
GET /v1/models 를 그대로 공개
```

## 5. 권장 최종 구조

### 5.1 배포 서버

Oracle Cloud 서버는 DABOYEO의 공식 발표 서버다.

```text
역할:
  정적 프론트 서빙
  /api/recommendations 제공
  TiDB/MySQL 연결
  추천 후보 조회
  후보 점수 계산
  AI gateway 호출
  fallback 추천 제공

하지 않는 일:
  Codex OAuth 토큰 저장
  openai-oauth 실행
  발표컴 또는 작업컴의 localhost 직접 접근
```

### 5.2 작업컴 AI Gateway

작업컴은 발표 시간 동안만 AI 분석 보조 서버 역할을 한다.

```text
역할:
  Codex/ChatGPT OAuth 로그인 유지
  openai-oauth 실행
  localhost openai-oauth를 내부 호출
  외부에는 DABOYEO 전용 /api/movie-analysis만 제공
  demo key 검사
  요청 크기와 후보 개수 제한
```

### 5.3 발표컴

발표컴은 가능하면 아무 설치 없이 브라우저만 쓴다.

```text
역할:
  배포 사이트 URL 접속
  AI 추천 버튼 클릭
  결과 시연

하지 않는 일:
  Codex 설치 필수화
  openai-oauth 실행 필수화
```

발표컴에 Codex를 설치하는 방식도 가능하지만, 현재 최우선 구조는 작업컴 gateway 방식이다. 발표컴 세팅 변수가 줄어들기 때문이다.

## 6. 요청 흐름 상세

### 6.1 프론트 요청

사용자가 AI 추천 플로우를 완료하면 프론트는 현재처럼 `/api/recommendations`를 호출한다.

예시 payload:

```json
{
  "anonymousId": "anon_demo",
  "mode": "precise",
  "survey": {
    "audience": "friends",
    "mood": "light",
    "avoid": ["violence", "sad_ending"]
  },
  "posterChoices": {
    "likedSeedMovieIds": ["movie-1", "movie-7", "movie-9", "movie-13", "movie-20"],
    "dislikedSeedMovieIds": []
  },
  "searchFilters": {
    "region": "강남",
    "date": "2026-04-29",
    "timeRange": "night",
    "personCount": 2
  }
}
```

프론트는 AI gateway URL이나 demo key를 알면 안 된다. 배포 서버가 gateway를 호출한다.

### 6.2 Spring 서버 처리

Spring 서버는 다음 순서로 처리한다.

```text
1. anonymousId 정규화 또는 새 세션 생성
2. survey, posterChoices, searchFilters 정규화
3. recommendation_profiles에서 기존 취향 가중치 조회
4. PreferenceProfileBuilder로 TagProfile 생성
5. showtimes 기반 후보 조회
6. RecommendationScorer로 1차 점수 계산
7. 상위 후보 중 영화 중복을 줄여 AI 후보 선택
8. AI provider 호출
9. AI 응답이 유효하면 AI 순서와 이유를 반영
10. AI 실패 또는 빈 응답이면 fallbackItems 사용
11. recommendation_runs에 실행 기록 저장
12. 프론트에 RecommendationResponse 반환
```

### 6.3 AI provider 입력

AI에게는 DB 전체를 보내지 않는다. 후보별 최소 정보만 보낸다.

예시:

```json
[
  {
    "id": 1024,
    "t": "영화 제목",
    "b": ["#가볍게", "#친구랑", "#코미디"],
    "vp": ["#20:10상영", "#좌석여유", "#예매가능"]
  }
]
```

AI가 받아야 하는 지시는 강하게 제한한다.

```text
주어진 후보 안에서만 고른다.
없는 영화, 없는 시간, 없는 극장을 만들지 않는다.
JSON만 반환한다.
후보 id만 반환한다.
추천 이유는 짧은 한국어 태그로 제한한다.
내부 점수, matchedTags, penalties 같은 raw token은 노출하지 않는다.
```

### 6.4 AI provider 출력

현재 빠른 추천은 이런 형태를 기대한다.

```json
{
  "r": [
    {
      "id": 1024,
      "why": "#가볍게 #친구랑",
      "v": "#20:10상영 #좌석여유"
    }
  ]
}
```

정밀 추천은 이런 형태를 기대한다.

```json
{
  "r": [
    {
      "id": 1024,
      "a": "#코미디취향"
    }
  ]
}
```

Spring 서버는 이 응답을 그대로 믿지 않는다.

```text
id가 후보 목록에 있는지 확인한다.
빈 이유나 약한 이유는 서버 fallback 문구로 대체한다.
내부 토큰이나 이상한 문구는 sanitize한다.
최종 추천은 항상 실제 DB 후보 기반으로만 만든다.
```

## 7. 구현 단계

### 7.1 1단계: 설정 이름과 provider 개념 정리

현재 설정:

```env
DABOYEO_LM_STUDIO_BASE_URL=http://127.0.0.1:1234/v1
DABOYEO_RECOMMEND_FAST_MODEL=gemma-4-e2b-it
DABOYEO_RECOMMEND_PRECISE_MODEL=gemma-4-e4b-it
```

새 설정 후보:

```env
DABOYEO_RECOMMEND_AI_PROVIDER=local-openai-compatible
DABOYEO_RECOMMEND_AI_BASE_URL=http://127.0.0.1:1234/v1
DABOYEO_RECOMMEND_AI_GATEWAY_KEY=
DABOYEO_RECOMMEND_FAST_MODEL=gemma-4-e2b-it
DABOYEO_RECOMMEND_PRECISE_MODEL=gemma-4-e4b-it
```

배포 발표용:

```env
DABOYEO_RECOMMEND_AI_PROVIDER=remote-gateway
DABOYEO_RECOMMEND_AI_BASE_URL=https://<presentation-ai-gateway>/api/movie-analysis
DABOYEO_RECOMMEND_AI_GATEWAY_KEY=<server-only-demo-key>
DABOYEO_RECOMMEND_FAST_MODEL=<gateway-fast-model>
DABOYEO_RECOMMEND_PRECISE_MODEL=<gateway-precise-model>
```

호환성을 위해 기존 `DABOYEO_LM_STUDIO_BASE_URL`은 당장 제거하지 않는다. 새 설정이 없으면 기존 값을 fallback으로 쓴다.

### 7.2 2단계: AI client 추상화

권장 인터페이스:

```java
public interface RecommendationAiClient {
    Optional<AiResult> rankAndExplain(
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates
    );
}
```

구현 후보:

```text
OpenAiCompatibleRecommendationClient
  LM Studio, Ollama OpenAI-compatible, openai-oauth compatible gateway에 사용

RemoteMovieAnalysisGatewayClient
  작업컴의 전용 /api/movie-analysis gateway에 사용

NoopRecommendationAiClient
  AI를 꺼야 할 때 Optional.empty() 반환
```

`RecommendationService`는 구체 구현체 이름을 몰라야 한다.

```text
기존:
RecommendationService -> LocalModelRecommendationClient

변경:
RecommendationService -> RecommendationAiClient
```

### 7.3 3단계: 최소 변경 구현안

가장 빠른 구현은 기존 `LocalModelRecommendationClient`를 크게 바꾸지 않는 방식이다.

```text
현재 client는 baseUrl + /chat/completions를 호출한다.
작업컴 gateway가 외부에 OpenAI-compatible /v1을 제공하게 만들면 Spring 코드는 거의 그대로 둔다.
대신 gateway는 raw openai-oauth가 아니라 DABOYEO 전용 wrapper여야 한다.
Spring은 X-Daboyeo-Demo-Key 같은 헤더를 붙인다.
```

이 방식의 장점:

```text
기존 prompt, response_format, parseResult를 재사용한다.
Java 변경량이 작다.
LM Studio와 Codex gateway를 env만 바꿔 교체하기 쉽다.
```

주의:

```text
외부 gateway의 /v1은 raw openai-oauth /v1이 아니다.
겉으로 OpenAI-compatible일 뿐, 내부에서 demo key와 요청 제한을 검사하는 wrapper다.
```

### 7.4 4단계: 더 안전한 구현안

더 안전한 방식은 Spring이 domain-specific gateway를 호출하는 것이다.

```text
Spring
-> POST https://<gateway>/api/movie-analysis

Gateway
-> 내부 openai-oauth /v1/chat/completions 호출
```

Gateway 요청 예시:

```json
{
  "mode": "precise",
  "model": "demo-model",
  "profile": {
    "audience": "friends",
    "mood": "light",
    "avoid": ["violence", "sad_ending"],
    "likedGenres": ["genre:comedy"]
  },
  "candidates": [
    {
      "showtimeId": 1024,
      "title": "영화 제목",
      "reasonHints": ["#가볍게", "#친구랑"],
      "valueHints": ["#20:10상영", "#좌석여유"]
    }
  ]
}
```

Gateway 응답 예시:

```json
{
  "model": "codex-oauth-demo",
  "rawJson": "{\"r\":[{\"id\":1024,\"a\":\"#코미디취향\"}]}",
  "picks": [
    {
      "showtimeId": 1024,
      "reason": "",
      "caution": "",
      "valuePoint": "",
      "analysisPoint": "#코미디취향"
    }
  ]
}
```

이 방식의 장점:

```text
외부 API가 영화 분석 용도로만 제한된다.
openai-oauth의 범용 API 형태가 외부로 새지 않는다.
Gateway에서 candidate 개수, 문자열 길이, 모드, 모델을 강하게 제한할 수 있다.
```

단점:

```text
Java client를 하나 더 만들어야 한다.
Gateway request/response DTO를 정의해야 한다.
테스트가 조금 늘어난다.
```

포트폴리오 발표 안정성을 생각하면 4단계 방식이 더 깔끔하다. 시간이 부족하면 3단계로 시작하고, 최소 보안 장치를 반드시 넣는다.

## 8. 작업컴 AI Gateway 요구사항

작업컴 gateway는 작은 서버 하나면 충분하다. Node, Python, Spring 중 아무거나 가능하지만, 발표 준비 속도만 보면 Node가 편하다.

### 8.1 실행 전제

```text
작업컴에 Node.js 설치
작업컴에서 Codex/ChatGPT OAuth 로그인 완료
작업컴에서 openai-oauth 실행
작업컴에서 AI Gateway 실행
터널 도구로 AI Gateway만 외부 노출
```

예시 흐름:

```powershell
npx @openai/codex login
npx openai-oauth
```

그리고 별도 터미널:

```powershell
node scripts/ai-gateway/server.js
```

그리고 터널:

```powershell
cloudflared tunnel --url http://127.0.0.1:<gateway-port>
```

명령은 예시다. 실제 도구는 Cloudflare Tunnel, ngrok, Tailscale Funnel 중 하나로 정한다.

### 8.2 Gateway 환경변수

```env
DABOYEO_GATEWAY_PORT=18080
DABOYEO_GATEWAY_KEY=<presentation-demo-key>
OPENAI_OAUTH_BASE_URL=http://127.0.0.1:10531/v1
OPENAI_OAUTH_FAST_MODEL=<fast-model>
OPENAI_OAUTH_PRECISE_MODEL=<precise-model>
```

주의:

```text
DABOYEO_GATEWAY_KEY는 문서나 커밋에 남기지 않는다.
Codex OAuth auth 파일은 절대 커밋하지 않는다.
openai-oauth 토큰 경로를 로그에 찍지 않는다.
```

### 8.3 Gateway 필수 방어선

```text
X-Daboyeo-Demo-Key 없으면 401
Content-Type application/json 아니면 415 또는 400
body 크기 제한
candidates 최대 8개
각 후보 title/reasonHints/valueHints 길이 제한
mode는 fast 또는 precise만 허용
응답은 JSON만 허용
openai-oauth 에러는 외부에 자세히 노출하지 않음
로그에 prompt 전체를 남기지 않음
터널은 발표 끝나면 종료
```

### 8.4 Gateway health check

외부 확인용 health endpoint는 민감 정보를 반환하지 않는다.

```http
GET /health
```

응답 예시:

```json
{
  "status": "ok",
  "provider": "codex-oauth-gateway"
}
```

`/health`는 OAuth 모델 목록, 토큰 경로, 계정 정보, 내부 base URL을 반환하지 않는다.

## 9. Oracle Cloud 배포 설정

Oracle Cloud 서버에는 Spring Boot jar와 필요한 환경변수만 둔다.

### 9.1 서버 환경변수

예시:

```env
DABOYEO_BACKEND_PORT=5500
TIDB_HOST=<tidb-host>
TIDB_PORT=4000
TIDB_USER=<db-user>
TIDB_PASSWORD=<db-password>
TIDB_DATABASE=<db>
TIDB_SSL=true
DABOYEO_FLYWAY_ENABLED=false
DABOYEO_FRONTEND_ORIGINS=https://<deployed-domain>

DABOYEO_RECOMMEND_AI_PROVIDER=remote-gateway
DABOYEO_RECOMMEND_AI_BASE_URL=https://<presentation-ai-gateway>/api/movie-analysis
DABOYEO_RECOMMEND_AI_GATEWAY_KEY=<server-only-demo-key>
DABOYEO_RECOMMEND_FAST_MODEL=<fast-model>
DABOYEO_RECOMMEND_PRECISE_MODEL=<precise-model>
DABOYEO_RECOMMEND_MIN_START_BUFFER_MINUTES=20
DABOYEO_RECOMMEND_FAST_AI_CANDIDATE_LIMIT=5
DABOYEO_RECOMMEND_PRECISE_AI_CANDIDATE_LIMIT=5
```

### 9.2 배포 서버가 가지면 안 되는 것

```text
Codex OAuth auth.json
ChatGPT 세션 쿠키
openai-oauth 내부 토큰
작업컴 로컬 파일 경로
터널 계정 토큰
```

Oracle 서버는 gateway URL과 demo key만 알면 된다.

## 10. fallback 전략

발표에서 가장 중요한 건 "AI가 느리거나 죽어도 화면이 죽지 않는 것"이다.

권장 fallback 순서:

```text
1순위: remote Codex OAuth Gateway
2순위: local OpenAI-compatible provider
3순위: 서버 deterministic fallback
```

현재 서버에는 이미 deterministic fallback이 있다. AI 호출이 실패하면 후보 점수 기반으로 추천을 만든다.

발표 UX 문구는 다음처럼 가져가면 된다.

```text
AI 연결 성공:
  "Codex가 후보를 재정렬하고 추천 이유를 작성했어."

AI 연결 실패:
  "AI 분석 연결이 불안정해서 서버 점수 기반 추천으로 보여줄게."
```

사용자에게 stack trace, gateway URL, token 관련 메시지를 보여주면 안 된다.

## 11. 프론트 변경 필요성

기본 구조에서는 프론트 변경이 거의 필요 없다.

현재 프론트는 이미 다음 흐름을 가진다.

```text
AI 추천 플로우 완료
-> requestRecommendations(payload)
-> /api/recommendations
-> 추천 결과 렌더링
```

Codex OAuth gateway를 붙여도 프론트는 계속 `/api/recommendations`만 호출한다.

다만 발표 완성도를 위해 선택적으로 추가할 수 있는 UI는 있다.

```text
AI provider 상태 배지
  예: AI 분석 연결됨, 서버 추천 모드

에러 문구 정리
  "로컬 Ollama" 같은 특정 모델명은 배포 발표 문맥에 맞게 "AI 분석 서버"로 변경

관리자/데모 전용 연결 확인 버튼
  일반 사용자에게는 숨기고, 발표 준비 때만 health check 용도로 사용
```

주의:

```text
프론트에 DABOYEO_RECOMMEND_AI_GATEWAY_KEY를 넣으면 안 된다.
프론트에서 tunnel URL을 직접 부르면 demo key가 노출될 수 있다.
배포 발표 기본 흐름은 프론트 -> Oracle Spring API -> gateway다.
```

## 12. 자연어 검색 확장 계획

현재 AI 추천 UI는 선택형 설문 중심이다. 나중에 자연어 입력을 붙이면 이 구조를 확장한다.

예시:

```text
사용자 입력:
  나 우울해

AI 분석:
  utteranceType = user_emotion
  emotion = sad
  strategy = comfort
  preferredMood = healing, warm, light
  avoidMood = bleak, self_harm, heavy_tragedy
```

반대 예시:

```text
사용자 입력:
  우울한 영화 추천해줘

AI 분석:
  utteranceType = content_request
  requestedMovieMood = sad
  strategy = match_requested_mood
```

이 확장은 별도 endpoint로 나누는 게 좋다.

```text
POST /api/recommendation/intent
  자연어를 추천 조건으로 바꾼다.

POST /api/recommendations
  정규화된 조건으로 DB 후보를 추천한다.
```

발표 1차 목표에서는 자연어 입력을 필수로 두지 않는다. 기존 선택형 조건과 포스터 취향만으로도 충분히 시연 가능하다.

## 13. 구현 작업 목록

### 13.1 백엔드

```text
1. RecommendationAiClient 인터페이스 추가
2. 기존 LocalModelRecommendationClient를 OpenAiCompatibleRecommendationClient로 정리
3. AI provider 설정 추가
4. Gateway 인증 헤더 설정 추가
5. RemoteMovieAnalysisGatewayClient 추가 여부 결정
6. RecommendationService가 인터페이스에 의존하도록 변경
7. AI 실패 시 fallback 동작 유지 확인
8. application.yml과 .env.example 업데이트
9. 테스트 추가 또는 기존 추천 서비스 테스트 보강
```

### 13.2 작업컴 Gateway

```text
1. scripts/ai-gateway/ 또는 별도 로컬 폴더에 gateway 생성
2. /health 추가
3. /api/movie-analysis 추가
4. X-Daboyeo-Demo-Key 검사
5. request body 제한
6. 내부 openai-oauth 호출
7. JSON 응답 파싱과 최소 검증
8. 오류 메시지 비식별화
9. 터널 연결 리허설
```

### 13.3 배포

```text
1. Oracle Cloud 서버에 Java 21 런타임 준비
2. Spring Boot jar 배포
3. TiDB/MySQL 환경변수 설정
4. CORS origin을 배포 도메인으로 제한
5. AI provider env 설정
6. systemd 또는 배포 플랫폼 프로세스 관리 설정
7. /api/health 확인
8. /api/recommendation/poster-seed 확인
9. /api/recommendations 확인
```

### 13.4 발표 리허설

```text
1. 작업컴 Codex OAuth 로그인 확인
2. 작업컴 openai-oauth 실행
3. 작업컴 gateway 실행
4. 터널 URL 발급
5. Oracle 서버 env에 tunnel URL 반영
6. Oracle 서버 재시작
7. 발표컴에서 배포 사이트 접속
8. AI 추천 버튼 클릭
9. AI 연결 성공 케이스 확인
10. gateway를 끄고 fallback 케이스 확인
```

## 14. 검증 계획

### 14.1 코드 검증

백엔드 구현 후:

```powershell
cd backend
gradle test
gradle bootJar
```

추천 관련 테스트만 빠르게 볼 때:

```powershell
cd backend
gradle test --tests kr.daboyeo.backend.service.recommendation.* --tests kr.daboyeo.backend.repository.recommendation.*
```

프론트 변경이 있다면:

```powershell
node --check frontend/src/js/api/client.js
node --check frontend/src/js/pages/daboyeoAi.js
```

공통:

```powershell
git diff --check
git status --short
```

### 14.2 로컬 API 검증

Spring 서버:

```powershell
Invoke-WebRequest -Uri http://127.0.0.1:5500/api/health -UseBasicParsing
```

포스터 seed:

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:5500/api/recommendation/poster-seed?limit=3" -UseBasicParsing
```

추천 API:

```powershell
Invoke-WebRequest -Uri http://127.0.0.1:5500/api/recommendations -Method POST -ContentType "application/json" -Body "<sample-json>" -UseBasicParsing
```

### 14.3 Gateway 검증

작업컴에서:

```powershell
Invoke-WebRequest -Uri http://127.0.0.1:<gateway-port>/health -UseBasicParsing
```

잘못된 키:

```text
POST /api/movie-analysis
-> 401이어야 한다.
```

정상 키:

```text
POST /api/movie-analysis
-> JSON picks를 반환해야 한다.
```

raw openai-oauth 노출 확인:

```text
https://<gateway-tunnel>/v1/models
-> 404 또는 401이어야 한다.

https://<gateway-tunnel>/v1/chat/completions
-> 404 또는 401이어야 한다.
```

### 14.4 발표 전 최종 점검

```text
배포 사이트가 열린다.
포스터 선택 단계가 열린다.
추천 API가 200을 반환한다.
AI gateway가 켜져 있으면 status=ok 또는 AI 기반 model명이 보인다.
AI gateway가 꺼져도 fallback 추천 결과가 나온다.
브라우저 화면에 토큰, gateway key, 내부 URL이 노출되지 않는다.
서버 로그에 OAuth token, auth 파일 경로, DB password가 찍히지 않는다.
```

## 15. 위험과 대응

### 15.1 터널이 끊김

대응:

```text
Spring fallback 추천 유지
발표 전에 tunnel 재시작 절차를 메모
AI 실패 문구를 자연스럽게 표시
```

### 15.2 openai-oauth 로그인 만료

대응:

```text
발표 전날 작업컴에서 로그인 확인
발표 직전 /health와 샘플 분석 요청 확인
재로그인 절차 준비
```

### 15.3 Codex 응답이 schema를 안 지킴

대응:

```text
Spring에서 JSON parse 실패 시 Optional.empty()
fallbackItems 사용
Gateway에서도 JSON 추출과 길이 제한
```

### 15.4 AI가 없는 영화 추천

대응:

```text
AI에게 title 기반 자유 추천을 시키지 않는다.
후보 id만 고르게 한다.
Spring이 후보 목록에 없는 id를 버린다.
최종 응답은 DB 후보에서만 생성한다.
```

### 15.5 계정 토큰 노출

대응:

```text
openai-oauth raw /v1 외부 공개 금지
auth.json 커밋 금지
gateway key 환경변수 처리
터널은 발표 후 종료
로그에 민감 정보 금지
```

## 16. 발표 설명 문장

발표에서는 이렇게 설명하면 된다.

```text
이 서비스는 영화 추천을 AI에게 통째로 맡기지 않습니다.
먼저 서버가 실제 상영 DB에서 현재 예매 가능한 후보를 조회하고,
가격, 좌석, 시간, 지역, 사용자의 선택 조건으로 1차 점수를 계산합니다.
그 다음 AI는 이 후보 안에서만 맥락을 분석해 재정렬하고,
사용자에게 보여줄 추천 이유를 생성합니다.
그래서 없는 영화나 현재 상영하지 않는 영화를 추천하는 문제를 줄였습니다.
```

Codex OAuth 구조는 이렇게 설명하면 된다.

```text
포트폴리오 발표에서는 유료 API 키를 서버에 올리지 않기 위해,
발표 시간 동안만 작업컴의 OAuth 기반 AI gateway를 연결합니다.
배포 서버에는 OAuth 토큰을 저장하지 않고,
AI gateway는 영화 추천 분석 요청만 받도록 제한했습니다.
AI 연결이 실패해도 서버 점수 기반 fallback 추천이 동작합니다.
```

## 17. 다음 구현 때의 우선순위

1순위:

```text
AI provider 설정 분리
Spring에서 gateway key 헤더 지원
기존 fallback 유지
```

2순위:

```text
작업컴 AI Gateway 구현
터널 연결 리허설
Oracle Cloud 환경변수 반영
```

3순위:

```text
프론트의 AI 연결 상태 문구 정리
자연어 검색/감정 분석 입력 확장
```

지금 단계의 핵심은 "배포 서버, DB 추천, AI 분석"의 책임 경계를 흐리지 않는 것이다. 이 경계만 지키면 로컬 AI에서 Codex OAuth gateway로 갈아타도 추천 API 구조는 크게 흔들리지 않는다.
