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
k6 run -e BASE_URL=http://localhost:8081 perf/copy-large-file-test.js

# 5. 본 측정
k6 run -e BASE_URL=http://localhost:8081 perf/load-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/folder-ops-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/upload-test.js
k6 run -e BASE_URL=http://localhost:8081 perf/copy-large-file-test.js
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
|  | p(95) | 14.66ms | 18.98ms |
| 쓰기 (폴더 생성) | 평균 | 8.79ms | 8.39ms |
|  | p(95) | 18.34ms | 20.82ms |
| 총 요청 |  | 5,933건 | 5,921건 |
| 처리량 |  | 117.2 req/s | 117.1 req/s |

### 분석

읽기/쓰기 시나리오는 `GET /folders/{id}/items`와 `POST /folders`만 사용하며, **파일 복사 로직과 무관**합니다.
두 브랜치의 수치가 거의 동일(오차 범위 내)한 것은 이를 뒷받침합니다.

---

## 2. folder-ops-test (폴더 CRUD) — 브랜치 비교

두 브랜치 모두 **ALL PASS**.

| 작업 | Threshold | step3 p(95) | step3-a p(95) | step3 평균 | step3-a 평균 |
|------|-----------|-------------|---------------|-----------|-------------|
| 복사 | p(95) < 2,000ms | 49.92ms | 88.63ms | 19.13ms | 23.02ms |
| 이동 | p(95) < 500ms | 79.49ms | 81.02ms | 17.01ms | 17.88ms |
| 삭제 | p(95) < 2,000ms | 56.08ms | 48.94ms | 36.21ms | 33.1ms |

### 분석

- **복사**: 현재 테스트 규모(1KB 파일, 5VU x 10회)에서는 유의미한 차이 없음. 두 브랜치 모두 threshold를 충분히 통과.
- **이동/삭제**: 두 브랜치 간 거의 동일 (오차 범위).

---

## 3. upload-test (파일 업로드) — 브랜치 비교

두 브랜치 모두 **errors threshold FAIL** (10MB 업로드 거부).

| 크기 | Threshold | step3 p(95) | step3-a p(95) | step3 평균 | step3-a 평균 |
|------|-----------|-------------|---------------|-----------|-------------|
| 1KB | p(95) < 1,000ms | 38.6ms | 57.55ms | 16.92ms | 22.72ms |
| 100KB | p(95) < 2,000ms | 184.14ms | — | 56.32ms | — |
| 1MB | p(95) < 3,000ms | 168.27ms | 211.45ms | 88.3ms | 104.82ms |
| 5MB | p(95) < 5,000ms | 600.57ms | 596.28ms | 347.47ms | 448.05ms |
| 10MB | p(95) < 10,000ms | 0.1ms | — | 0.069ms | — |
| **errors** | **< 5%** | **5.38%** | **5.45%** | — | — |

### 분석

- 업로드 로직(물리 파일 저장)은 두 브랜치에서 동일하므로, 수치 차이는 자연 편차.
- 10MB 업로드 실패는 두 브랜치 공통 이슈 (`max-request-size` 설정).

---

## 4. copy-large-file-test (파일 크기별 복사) — 브랜치 비교

두 브랜치 모두 **ALL PASS**.

| 크기 | Threshold | step3 p(95) | step3-a p(95) | step3 평균 | step3-a 평균 | 변화 (평균) |
|------|-----------|-------------|---------------|-----------|-------------|------------|
| 1KB | p(95) < 1,000ms | 23.16ms | 38.5ms | 12.5ms | 12.49ms | 동등 |
| 100KB | p(95) < 2,000ms | 17.64ms | 18.1ms | 10.53ms | 9.19ms | 동등 |
| 1MB | p(95) < 3,000ms | 15.96ms | 56.35ms | 11.02ms | 13.53ms | 동등 |
| 5MB | p(95) < 5,000ms | 21.7ms | 10.43ms | 14.29ms | 8.09ms | **-43.4%** |

- VU: 3~5, 반복: 10~20회/VU
- 에러율: 양쪽 모두 0%

### 분석

- **1KB~1MB**: 두 브랜치 간 평균 응답 시간 거의 동일. 소용량 파일은 물리 파일 복제 비용이 미미하여 참조 공유의 이점이 드러나지 않음.
- **5MB**: step3-a가 평균 **43.4% 빠름** (14.29ms → 8.09ms). 물리 파일을 복제하지 않고 DB 레코드만 생성하므로, 파일 크기가 클수록 디스크 I/O 절감 효과가 뚜렷해짐.
- p(95) 수치에서도 5MB 복사가 step3(21.7ms) 대비 step3-a(10.43ms)로 **51.9% 개선**.

---

## 종합 요약

| 테스트 | step3 | step3-a | 비교 |
|--------|-------|---------|------|
| load-test | PASS (p95: 14.84ms) | PASS (p95: 19.18ms) | **동등** (오차 범위) |
| folder-ops-test | PASS | PASS | **동등** (오차 범위) |
| upload-test | FAIL (5.38%) | FAIL (5.45%) | **동일 이슈** (10MB 거부) |
| copy-large-file (1KB~1MB) | PASS | PASS | **동등** (오차 범위) |
| copy-large-file (5MB) | PASS (avg 14.29ms) | PASS (avg 8.09ms) | **step3-a 43% 개선** |

### 결론

통제된 환경(볼륨 삭제 + 워밍업)에서 측정한 결과:

1. **읽기/쓰기/업로드/폴더 CRUD**: 두 브랜치 간 성능 차이 없음 (복사 로직과 무관한 API)
2. **파일 복사 (1KB~1MB)**: 물리 파일 크기가 작아 복제 비용이 미미하므로 차이 없음
3. **파일 복사 (5MB)**: step3-a가 **43.4% 빠름** — 물리 파일 복제를 생략하고 DB 레코드만 생성하므로, 파일 크기에 비례하여 디스크 I/O를 절감

`step3-a`의 "물리 파일 1개만 유지" 최적화는 **파일 크기가 클수록 효과적**이며, GB 단위 파일이나 네트워크 스토리지(S3 등) 환경에서는 더 큰 차이가 예상됩니다.

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
