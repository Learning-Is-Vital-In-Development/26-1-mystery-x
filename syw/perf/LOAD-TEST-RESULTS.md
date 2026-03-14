# k6 부하 테스트 결과 (2026-03-14)

## 테스트 환경

- Docker Compose: MySQL + Spring Boot App (port 8081)
- k6 v1.6.1
- 시드 데이터: 5명 owner x 100폴더 x (10 하위폴더 + 10 파일)

## 개선 사항 요약

| 개선 항목 | Before | After |
|-----------|--------|-------|
| 업로드 데이터 | `'x'.repeat()` 동일 바이트 반복 | `/dev/urandom` 랜덤 바이너리 fixtures (36개 파일, ~56MB) |
| 사용자 | `X-OWNER-ID: 1` 고정 | 5명 owner, VU별 `(__VU % OWNER_COUNT) + 1`로 분배 |
| 폴더 ID 조회 | `Math.random() * 100 + 1` (404 다수) | `setup()`에서 실존 ID 동적 조회 (404 0건) |
| 파일 로딩 | 매 iteration마다 문자열 생성 | k6 init 단계에서 `open('b')`로 1회 로드, VU 간 공유 |

## 수정된 파일

- `perf/generate-fixtures.sh` — 크기별 랜덤 바이너리 파일 생성 스크립트 (신규)
- `perf/seed-data.js` — fixtures 사용 + 다중 owner 시드
- `perf/load-test.js` — setup()에서 owner별 실존 폴더 ID 조회 + VU별 owner 분배
- `perf/upload-test.js` — 크기별 fixtures 랜덤 선택 + VU별 owner 분배
- `perf/folder-ops-test.js` — fixtures 사용 + VU별 owner 분배
- `.gitignore` — `perf/fixtures/*.bin` 추가

## 실행 방법

```bash
# 0. fixtures 생성 (최초 1회)
chmod +x perf/generate-fixtures.sh && perf/generate-fixtures.sh

# 1. 컨테이너 실행
docker compose up mysql app -d

# 2. 시드 데이터 생성
k6 run -e BASE_URL=http://localhost:8081 perf/seed-data.js

# 3. 테스트 실행
k6 run -e BASE_URL=http://localhost:8081 perf/load-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/folder-ops-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/upload-test.js
```

## 테스트 결과

### 1. load-test (읽기/쓰기 부하) — ALL PASS

| 메트릭 | Threshold | 실측 p(95) | 결과 |
|--------|-----------|-----------|------|
| http_req_duration | p(95) < 500ms | 183.17ms | PASS |
| errors | < 10% | 0% | PASS |

| 시나리오 | 평균 응답 | p(95) | 최대 VU | 총 요청 |
|----------|----------|-------|---------|---------|
| 읽기 (폴더 조회) | 41.9ms | 184.88ms | 50 | 3,893건 |
| 쓰기 (폴더 생성) | 42.04ms | 171.65ms | 20 | 1,373건 |

- 총 HTTP 요청: 5,266건 (103.3 req/s)
- 테스트 시간: 51초
- 에러율: 0%

### 2. folder-ops-test (폴더 CRUD) — ALL PASS

| 작업 | Threshold | 실측 p(95) | 평균 응답 | 결과 |
|------|-----------|-----------|----------|------|
| 복사 | p(95) < 2,000ms | 58.17ms | 29.4ms | PASS |
| 이동 | p(95) < 500ms | 47.48ms | 18.43ms | PASS |
| 삭제 | p(95) < 2,000ms | 79.95ms | 54.7ms | PASS |

- 총 HTTP 요청: 700건 (80.6 req/s)
- VU: 5, 반복: 50회
- 테스트 시간: 8.7초
- 에러율: 0%

### 3. upload-test (파일 업로드) — errors threshold FAIL

| 크기 | Threshold | 실측 p(95) | 평균 응답 | 결과 |
|------|-----------|-----------|----------|------|
| 1KB | p(95) < 1,000ms | 35.25ms | 14.83ms | PASS |
| 100KB | p(95) < 2,000ms | 41.15ms | 18.51ms | PASS |
| 1MB | p(95) < 3,000ms | 275.24ms | 130.31ms | PASS |
| 5MB | p(95) < 5,000ms | 694.35ms | 386.32ms | PASS |
| 10MB | p(95) < 10,000ms | 0.155ms | 0.075ms | PASS (즉시 거부) |
| **errors** | **< 5%** | **5.18% (80/1543)** | — | **FAIL** |

- 총 HTTP 요청: 1,543건 (12.3 req/s)
- 테스트 시간: 2분 5초
- 전송 데이터: 856MB

## 종합 요약

| 테스트 | 결과 | 비고 |
|--------|------|------|
| load-test (읽기/쓰기) | **ALL PASS** | p(95) 183ms, 에러 0% |
| folder-ops-test (CRUD) | **ALL PASS** | 복사/이동/삭제 모두 threshold 이내 |
| upload-test (업로드) | **FAIL** | 10MB 업로드 거부로 에러율 5.18% |

## 알려진 이슈

### 10MB 업로드 실패 (errors 5.18%)

- 10MB 업로드 응답 시간이 ~155µs로, 서버가 요청을 처리하지 않고 즉시 거부
- **근본 원인**: `application.yml`에서 `max-file-size`와 `max-request-size`가 모두 10MB로 설정됨. fixture 파일이 정확히 10MB(10,485,760 bytes)이지만, multipart 요청 시 boundary/헤더/payload JSON 파트 등의 오버헤드가 추가되어 `max-request-size` 제한(10MB)을 초과
- **해결 방안**: `spring.servlet.multipart.max-request-size`를 `15MB` 등으로 증가

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB   # multipart 오버헤드 감안
```
