# 0) DB 초기화 (개발용)
sqlite3 /data/26-1-mystery-x/buchoipark/data/sqlite/livid.db "DELETE FROM files;"
rm -rf /data/26-1-mystery-x/buchoipark/data/uploads/*
rm -rf ./test.txt

# 1) 업로드
UPLOAD_RES=$(curl -s -X POST "http://localhost:8080/files/upload" \
  -F "userId=user-123" \
  -F "filePath=/docs/test.txt" \
  -F "file=@/data/26-1-mystery-x/buchoipark/src/test/upload-original/test.txt")

echo "$UPLOAD_RES"

# 2) 업로드 응답에서 id 추출 (jq 필요)
FILE_ID=$(echo "$UPLOAD_RES" | jq -r '.id')

# 3) 전체 목록 조회
echo "전체 파일 목록:"
curl -s "http://localhost:8080/files" | jq

# 4) 사용자 필터 목록 조회
echo "사용자 필터 목록:"
curl -s "http://localhost:8080/files?userId=user-123" | jq -r '.[0].id'

# 5) 다운로드
curl -OJ "http://localhost:8080/files/${FILE_ID}/download" 

# 6) 파일 이동 (메타데이터만 변경, 폴더 경로만 지정해도 파일명 자동 보정)
echo "파일 이동:"
curl -s -X POST "http://localhost:8080/files/${FILE_ID}/move" \
  -H "Content-Type: application/json" \
  -d '{"filePath":"/virtual/moved/"}'

echo "업데이트 후 전체 파일 목록:"
curl -s "http://localhost:8080/files" | jq


# 7) 폴더 이동 (prefix 기반 일괄 변경)
echo "폴더 이동:"
curl -s -X POST "http://localhost:8080/files/move-folder" \
  -H "Content-Type: application/json" \
  -d '{"fromPath":"/docs","toPath":"/docs2"}'

echo "업데이트 후 전체 파일 목록:"
curl -s "http://localhost:8080/files" | jq