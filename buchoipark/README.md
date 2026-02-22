# cloud file storage

클라우드 기반의 파일 저장소 서비스입니다. 사용자별로 파일을 업로드, 다운로드, 이동할 수 있으며 폴더 단위의 일괄 관리도 지원합니다.

## 주요 기능

- **파일 업로드**: 사용자별로 파일을 업로드하고 저장소에 저장
- **파일 다운로드**: 파일 ID로 파일을 다운로드
- **파일 이동**: 개별 파일의 경로를 변경
- **폴더 단위 이동**: 특정 경로 하위의 모든 파일을 다른 경로로 이동
- **파일 목록 조회**: 전체 또는 사용자별 파일 목록 조회
- **헬스 체크**: 서버 상태 확인

## dir 구성

### data

- `sqlite/`: SQLite 데이터베이스 파일 저장 디렉토리
- `uploads/`: 업로드된 파일들이 저장되는 디렉토리

### deploy

- `docker-compose.yaml`: Docker Compose 설정 파일
  - `server-debug` 서비스: Spring Boot 애플리케이션을 개발 환경에서 실행
  - 소스 코드와 데이터 디렉토리를 볼륨으로 마운트하여 수정 사항 실시간 반영 가능
- `Dockerfile`: 다중 스테이지 빌드로 애플리케이션 컨테이너화

### src

- Spring Boot 기반 REST API 서버
- `FileController`: 파일 관련 API 엔드포인트
  - `GET /health`: 서버 상태 확인
  - `POST /files/upload`: 파일 업로드
  - `GET /files`: 파일 목록 조회
  - `GET /files/{id}/download`: 파일 다운로드
  - `POST /files/{id}/move`: 파일 이동
  - `POST /files/move-folder`: 폴더 단위 이동
- `FileService`: 파일 관리 로직
- SQLite를 데이터베이스로 사용

## build

```bash
cd src
./gradlew clean bootJar
```

## quick start

### Docker Compose를 이용한 실행

```bash
cd deploy
docker compose up --build -d
```

### 테스트

```bash
# 서버 상태 확인
curl http://localhost:8080/health

# 파일 업로드
curl -X POST "http://localhost:8080/files/upload" \
  -F "userId=user-123" \
  -F "filePath=/docs/test.txt" \
  -F "file=@test.txt"

# 파일 목록 조회
curl http://localhost:8080/files

# 사용자별 파일 조회
curl "http://localhost:8080/files?userId=user-123"

# 파일 다운로드 (FILE_ID는 업로드 응답의 id 값)
curl -OJ "http://localhost:8080/files/{FILE_ID}/download"

# 파일 이동
curl -X POST "http://localhost:8080/files/{FILE_ID}/move" \
  -H "Content-Type: application/json" \
  -d '{"filePath":"/moved/"}'

# 폴더 단위 이동
curl -X POST "http://localhost:8080/files/move-folder" \
  -H "Content-Type: application/json" \
  -d '{"fromPath":"/docs","toPath":"/docs2"}'
```
