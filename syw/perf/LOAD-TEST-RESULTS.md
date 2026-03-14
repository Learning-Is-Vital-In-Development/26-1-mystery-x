# k6 부하 테스트 결과 (2026-03-14)

## 테스트 환경

- Docker Compose: MySQL + Spring Boot App (port 8081)
- k6 v1.6.1
- 시드 데이터: 5명 owner x 100폴더 x (10 하위폴더 + 10 파일)

## 브랜치 비교

| 브랜치 | 주요 변경 |
|--------|----------|
| `feature/step3` | 기존 구현 (파일 복사 시 물리 파일도 복제) |
| `feature/step3-a` | 파일 복사 시 물리 파일 1개만 유지 (참조 공유) |

---

## 테스트 절차

정확한 비교를 위해, 각 브랜치마다 아래 절차를 **동일하게** 수행했습니다.

> **왜 이 절차가 필요한가?**
> - MySQL Buffer Pool, JVM JIT 컴파일, OS 디스크 캐시 등이 이전 테스트 결과에 영향을 줄 수 있음
> - 컨테이너만 재시작하면 볼륨(DB 데이터)이 남아 동일 조건 재현이 불가
> - 워밍업 없이 바로 측정하면 JVM Cold Start로 인해 초반 응답이 비정상적으로 느림

```bash
# 1. 컨테이너 + 볼륨 완전 삭제 (DB 초기화)
docker compose down -v

# 2. 해당 브랜치로 빌드 및 컨테이너 시작
docker compose up mysql app -d --build

# 3. 시드 데이터 생성
k6 run -e BASE_URL=http://localhost:8081 perf/seed-data.js

# 4. 워밍업 (결과 버림) — JVM JIT + DB 캐시 안정화
k6 run -e BASE_URL=http://localhost:8081 perf/load-test.js

# 5. 본 측정
k6 run -e BASE_URL=http://localhost:8081 perf/load-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/folder-ops-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/upload-test.js
```

---

## 1. load-test (읽기/쓰기 부하) — 브랜치 비교

두 브랜치 모두 **ALL PASS**.

### Threshold 결과

| 메트릭 | Threshold | step3 p(95) | step3-a p(95) |
|--------|-----------|-------------|---------------|
| http_req_duration | p(95) < 500ms | 14.84ms | 19.18ms |
| errors | < 10% | 0% | 0% |

### 시나리오별 상세

| 시나리오 | 메트릭 | step3 | step3-a |
|----------|--------|-------|---------|
| 읽기 (폴더 조회) | 평균 | 6.58ms | 7.26ms |
| | p(95) | 14.66ms | 18.98ms |
| 쓰기 (폴더 생성) | 평균 | 8.79ms | 8.39ms |
| | p(95) | 18.34ms | 20.82ms |
| 총 요청 | | 5,933건 | 5,921건 |
| 처리량 | | 117.2 req/s | 117.1 req/s |

### 분석

읽기/쓰기 시나리오는 `GET /folders/{id}/items`와 `POST /folders`만 사용하며, **파일 복사 로직과 무관**합니다.
두 브랜치의 수치가 거의 동일(오차 범위 내)한 것은 이를 뒷받침합니다.

> 참고: 이전 비통제 테스트에서는 step3-a가 67% 빠르게 나왔으나, 이는 JVM/DB 캐시 워밍업 차이에 의한 환경 편차였습니다.

---

## 2. folder-ops-test (폴더 CRUD) — 브랜치 비교

두 브랜치 모두 **ALL PASS**.

| 작업 | Threshold | step3 p(95) | step3-a p(95) | step3 평균 | step3-a 평균 |
|------|-----------|-------------|---------------|-----------|-------------|
| 복사 | p(95) < 2,000ms | 49.92ms | 88.63ms | 19.13ms | 23.02ms |
| 이동 | p(95) < 500ms | 79.49ms | 81.02ms | 17.01ms | 17.88ms |
| 삭제 | p(95) < 2,000ms | 56.08ms | 48.94ms | 36.21ms | 33.1ms |

### 분석

- **복사**: step3-a가 물리 파일 복제를 생략하지만, 현재 테스트 규모(1KB 파일, 5VU x 10회)에서는 유의미한 차이가 나타나지 않음. 두 브랜치 모두 threshold를 충분히 통과.
- **이동/삭제**: 두 브랜치 간 거의 동일 (오차 범위).
- 복사 최적화의 효과는 **대용량 파일 복사** 또는 **대량 복사 작업**에서 더 두드러질 것으로 예상.

---

## 3. upload-test (파일 업로드) — 브랜치 비교

두 브랜치 모두 **errors threshold FAIL** (10MB 업로드 거부).

| 크기 | Threshold | step3 p(95) | step3-a p(95) | step3 평균 | step3-a 평균 |
|------|-----------|-------------|---------------|-----------|-------------|
| 1KB | p(95) < 1,000ms | 38.6ms | 57.55ms | 16.92ms | 22.72ms |
| 100KB | p(95) < 2,000ms | 184.14ms | — | 56.32ms | — |
| 1MB | p(95) < 3,000ms | 168.27ms | 211.45ms | 88.3ms | 104.82ms |
| 5MB | p(95) < 5,000ms | 600.57ms | 596.28ms | 347.47ms | 448.05ms |
| 10MB | p(95) < 10,000ms | 0.1ms | — | 0.069ms | — | 즉시 거부 |
| **errors** | **< 5%** | **5.38%** | **5.45%** | — | — |

> step3-a 100KB/10MB 상세 메트릭은 출력 캡처 범위 밖으로 누락. 전체 에러율과 나머지 크기별 수치로 비교.

### 분석

- 업로드 로직(물리 파일 저장)은 두 브랜치에서 동일하므로, 수치 차이는 자연 편차.
- 10MB 업로드 실패는 두 브랜치 공통 이슈 (`max-request-size` 설정).

---

## 종합 요약

| 테스트 | step3 | step3-a | 비교 |
|--------|-------|---------|------|
| load-test | PASS (p95: 14.84ms) | PASS (p95: 19.18ms) | **동등** (오차 범위) |
| folder-ops-test | PASS | PASS | **동등** (오차 범위) |
| upload-test | FAIL (5.38%) | FAIL (5.45%) | **동일 이슈** (10MB 거부) |

### 결론

통제된 환경(볼륨 삭제 + 워밍업)에서 측정한 결과, **두 브랜치 간 성능 차이는 오차 범위 내**입니다.

`step3-a`의 "물리 파일 1개만 유지" 최적화는 현재 테스트 시나리오(1KB 파일, 소규모 복사)에서는 유의미한 차이를 만들지 않습니다.
이 최적화의 효과가 나타나려면:

- **대용량 파일** (MB~GB 단위) 복사 시나리오
- **대량 복사** (수백~수천 건 동시) 시나리오
- **디스크 I/O가 병목**인 환경 (HDD, 네트워크 스토리지 등)

에서 별도 테스트가 필요합니다.

---

## 알려진 이슈

### 10MB 업로드 실패

- 10MB 업로드 응답 시간이 ~100µs로, 서버가 요청을 처리하지 않고 즉시 거부
- **근본 원인**: `application.yml`에서 `max-file-size`와 `max-request-size`가 모두 10MB로 설정됨. fixture 파일이 정확히 10MB(10,485,760 bytes)이지만, multipart 요청 시 boundary/헤더/payload JSON 파트 등의 오버헤드가 추가되어 `max-request-size` 제한(10MB)을 초과
- **해결 방안**: `spring.servlet.multipart.max-request-size`를 `15MB` 등으로 증가

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB   # multipart 오버헤드 감안
```
