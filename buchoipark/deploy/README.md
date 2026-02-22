# SQLite Container

이 프로젝트는 `sqlite` 전용 컨테이너를 `docker-compose.yml`로 분리 구성했습니다.

## 실행

```bash
docker compose up -d sqlite
```

## SQLite 접속

```bash
docker compose exec sqlite sqlite3 /data/livid.db
```

## 외부 Kotlin 프로젝트 연결

SQLite는 서버형 DB가 아니라 TCP 포트를 열어 연결하지 않습니다.

- JDBC URL 예시: `jdbc:sqlite:/home/ubuntu/livid/data/sqlite/livid.db`

## DB 파일 위치

- 컨테이너 내부: `/data/livid.db`
- 호스트 경로: `./data/sqlite/livid.db`
