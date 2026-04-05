# loadtest — 파일 서버 벤치마크

LeeHeoYoon 파일 서버의 사용자 관점 성능을 6개 시나리오로 평가하는 Go 기반 벤치마크 도구입니다.

## 시나리오

| 순서 | 시나리오 | 설명 |
|------|----------|------|
| 1 | Upload | 2단계 업로드 (init + Nginx direct-to-disk) 또는 multipart |
| 2 | Download | 동시 다운로드 + SHA-256 해시 검증 |
| 3 | Folder List | 폴더 목록 조회 + 파일 수 검증 |
| 4 | Move Folder | 폴더 이동 + 대상 경로 검증 |
| 5 | Delete Files | 개별 파일 동시 삭제 + 빈 폴더 검증 |
| 6 | Delete Folder | 폴더 단위 삭제 + 하위 전체 삭제 검증 |

## 테스트 파일 케이스 (총 ~15GB)

| 케이스 | 크기 | 파일 수 | 동시성 |
|--------|------|---------|--------|
| small | 3 MB | 1000 | 1000 |
| medium | 30 MB | 100 | 100 |
| large | 100 MB | 30 | 30 |
| xlarge | 300 MB | 10 | 10 |
| huge | 500 MB | 6 | 6 |

## 빠른 시작

### 1. 서버 실행

```bash
cd LeeHeoYoon/docker
cp .env.example .env
# .env 파일에서 DB_PASSWORD 설정
docker compose up -d
```

### 2. loadtest 컨테이너 실행

**방법 A: 서버 docker-compose 네트워크에 연결 (권장)**

```bash
cd LeeHeoYoon/loadtest
docker compose up -d
docker exec -it loadtest bash
```

> `docker-compose.yaml`의 `networks.server-net.name`이 서버의 네트워크 이름과 일치해야 합니다.
> 기본값은 `docker_default`이며, `docker network ls`로 확인할 수 있습니다.

**방법 B: 서버 docker-compose에 직접 추가**

`LeeHeoYoon/docker/docker-compose.yml`에 다음 서비스를 추가:

```yaml
  loadtest:
    image: golang:1.24-bookworm
    container_name: loadtest
    working_dir: /work/src
    volumes:
      - ../loadtest/src:/work/src
    stdin_open: true
    tty: true
    command: "bash -c 'tail -f /dev/null'"
    cpus: "4.0"
    mem_limit: 8g
```

### 3. 빌드

```bash
bash build.sh
```

두 개의 바이너리가 생성됩니다:
- `create-test-files` — 테스트 파일 생성 도구
- `benchmark` — 벤치마크 실행 도구

### 4. 테스트 파일 생성

```bash
./create-test-files -data-dir ./bench-data -seed 42
```

### 5. 벤치마크 실행

```bash
# 2단계 업로드 (Nginx direct-to-disk, 기본)
./benchmark -host http://nginx:8080 -user-id 1

# multipart 업로드 (전통 방식)
./benchmark -host http://nginx:8080 -user-id 1 -upload-mode multipart
```

## CLI 플래그

### benchmark

| 플래그 | 기본값 | 설명 |
|--------|--------|------|
| `-host` | `http://nginx:8080` | 대상 서버 URL (Nginx 경유) |
| `-user-id` | `1` | X-User-Id 헤더 값 |
| `-upload-mode` | `raw` | 업로드 방식 (`raw`: 2단계, `multipart`: 전통) |
| `-data-dir` | `./bench-data` | 테스트 파일 디렉토리 |
| `-seed` | `42` | 파일 생성 랜덤 시드 |
| `-timeout` | `5m` | 요청별 타임아웃 |

### create-test-files

| 플래그 | 기본값 | 설명 |
|--------|--------|------|
| `-data-dir` | `./bench-data` | 출력 디렉토리 |
| `-seed` | `42` | 랜덤 시드 |

## API 매핑

벤치마크가 사용하는 엔드포인트:

| 기능 | 메서드 | 경로 |
|------|--------|------|
| 폴더 생성 | POST | `/api/folders` |
| 폴더 목록 | GET | `/api/folders/{id}/contents` |
| 폴더 이동 | PATCH | `/api/folders/{id}/move` |
| 폴더 삭제 | DELETE | `/api/folders/{id}` |
| 업로드 초기화 | POST | `/api/files/init` |
| 업로드 (raw) | POST | `/upload/{metadataId}?token={token}` |
| 업로드 (multipart) | POST | `/api/files` |
| 다운로드 | GET | `/api/files/{id}` |
| 파일 삭제 | DELETE | `/api/files/{id}` |

모든 요청에 `X-User-Id` 헤더가 필수입니다.
