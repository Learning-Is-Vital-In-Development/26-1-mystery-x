## benchmark — LeeHeoYoon 파일 서버 벤치마크 스펙

### 대상 서버

- Spring Boot 3.4.2 (Virtual Threads, ZGC)
- Nginx reverse proxy (X-Accel-Redirect, direct-to-disk upload)
- PostgreSQL 16 (LTREE 확장)
- ID 기반 파일/폴더 관리 (X-User-Id 헤더 인증)

### 테스트 파일 케이스: 3G x 5

- 3MB x 1000 concurrent
- 30MB x 100 concurrent
- 100MB x 30 concurrent
- 300MB x 10 concurrent
- 500MB x 6 concurrent

---

### API 스펙

#### POST /api/folders — 폴더 생성

```
POST /api/folders
X-User-Id: <userId>
Content-Type: application/json

{ "name": "folderA", "parentId": 1 }   // parentId=null 이면 루트

응답: 201 Created
{ "id": 2, "name": "folderA", "parentId": 1, "createdAt": "..." }
```

#### GET /api/folders/{id}/contents — 폴더 내용 조회

```
GET /api/folders/{folderId}/contents?page=0&size=200
X-User-Id: <userId>

응답: 200 OK
{
  "folderId": 1,
  "folderName": "small",
  "folders": [{ "id": 2, "name": "folderA", ... }],
  "files": [{ "id": 10, "originalName": "small_0001.bin", "fileSize": 3145728, ... }]
}
```

#### GET /api/folders/root/contents — 루트 폴더 조회

```
GET /api/folders/root/contents?page=0&size=200
X-User-Id: <userId>

응답: 200 OK (FolderContentsResponse)
```

#### PATCH /api/folders/{id}/move — 폴더 이동

```
PATCH /api/folders/{folderId}/move
X-User-Id: <userId>
Content-Type: application/json

{ "targetFolderId": 5 }

응답: 200 OK (FolderResponse)
```

#### DELETE /api/folders/{id} — 폴더 삭제 (하위 전체)

```
DELETE /api/folders/{folderId}
X-User-Id: <userId>

응답: 204 No Content
```

---

#### POST /api/files/init — 업로드 초기화

```
POST /api/files/init
X-User-Id: <userId>
Content-Type: application/json

{ "fileName": "test.bin", "fileSize": 3145728, "contentType": "application/octet-stream", "folderId": 2 }

응답: 200 OK
{ "metadataId": 42, "uploadToken": "abc-def-123" }
```

#### POST /upload/{metadataId}?token={token} — 파일 업로드 (Nginx direct-to-disk)

```
POST /upload/{metadataId}?token={uploadToken}
Content-Type: application/octet-stream

Body: raw binary

응답: 200 OK (FileResponse - Nginx callback 경유)
```

#### POST /api/files — 파일 업로드 (multipart)

```
POST /api/files
X-User-Id: <userId>
Content-Type: multipart/form-data

Form fields:
  file      binary   — 파일 본문
  folderId  long     — 대상 폴더 ID (optional)

응답: 202 Accepted
{ "id": 10, "originalName": "test.bin", "fileSize": 3145728, "uploadStatus": "COMPLETED", ... }
```

#### GET /api/files/{id} — 파일 다운로드

```
GET /api/files/{fileId}
X-User-Id: <userId>

응답: 200 OK + 파일 바이너리 (Nginx X-Accel-Redirect 경유)
```

#### DELETE /api/files/{id} — 파일 삭제

```
DELETE /api/files/{fileId}
X-User-Id: <userId>

응답: 204 No Content
```

---

### 벤치마크 실행 순서

1. **Cleanup** — 이전 실행 데이터 정리 (bench 폴더 삭제)
2. **Create Folders** — bench/{small,medium,large,xlarge,huge}/{folderA,...} + 이동 대상 폴더
3. **Upload** — 케이스별 동시 업로드, 파일 ID 저장
4. **Download** — 업로드된 파일 동시 다운로드 + SHA-256 검증
5. **Folder List** — 각 폴더 내용 조회 + 파일 수 검증
6. **Move Folder** — 폴더 이동 (PATCH /api/folders/{id}/move) + 검증
7. **Delete Files** — 개별 파일 동시 삭제 + 빈 폴더 검증
8. **Delete Folder** — 폴더 단위 재귀 삭제 + 검증

### 폴더 구조

```
bench/
├── small/
│   ├── folderA/ (250개) → 이동 대상
│   ├── folderB/ (250개) → 이동 대상
│   ├── folderC/ (250개) → 개별 삭제 대상
│   └── folderD/ (250개) → 개별 삭제 대상
├── medium/
│   ├── folderA/ (50개) → 이동 대상
│   └── folderB/ (50개) → 개별 삭제 대상
├── large/
│   ├── folderA/ (15개) → 이동 대상
│   └── folderB/ (15개) → 폴더 삭제 대상
├── xlarge/
│   └── folderA/ (10개) → 이동 대상
├── huge/
│   └── folderA/ (6개) → 폴더 삭제 대상
├── moved_s_a/   ← 이동 대상 (→ 폴더 삭제)
├── moved_s_b/   ← 이동 대상
├── moved_m_a/   ← 이동 대상
├── moved_l_a/   ← 이동 대상
└── moved_xl/    ← 이동 대상
```
