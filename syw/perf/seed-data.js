import http from 'k6/http';
import { check } from 'k6';

// 대량 데이터 시드 스크립트 (1회 실행)
// k6 run perf/seed-data.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_ID = '1';
const FOLDER_COUNT = 100;      // 루트 폴더 수
const SUB_FOLDER_COUNT = 10;   // 폴더당 하위 폴더 수
const FILE_COUNT = 10;         // 폴더당 파일 수

export const options = {
    vus: 1,
    iterations: 1,
};

function createFolder(name, parentId) {
    const body = parentId
        ? JSON.stringify({ name, parentId })
        : JSON.stringify({ name });

    const res = http.post(`${BASE_URL}/folders`, body, {
        headers: {
            'Content-Type': 'application/json',
            'X-OWNER-ID': OWNER_ID,
        },
    });

    if (res.status === 200) {
        return JSON.parse(res.body).id;
    }
    console.error(`Failed to create folder: ${name}, status: ${res.status}`);
    return null;
}

function uploadFile(name, parentId) {
    const content = 'x'.repeat(1024); // 1KB dummy file
    const payload = parentId
        ? JSON.stringify({ parentId, itemType: 'FILE' })
        : JSON.stringify({ itemType: 'FILE' });

    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(content, name, 'text/plain'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, {
        headers: { 'X-OWNER-ID': OWNER_ID },
    });

    check(res, { 'file uploaded': (r) => r.status === 200 });
}

export default function () {
    console.log(`Seeding ${FOLDER_COUNT} folders with ${SUB_FOLDER_COUNT} subfolders and ${FILE_COUNT} files each...`);

    for (let i = 0; i < FOLDER_COUNT; i++) {
        const folderId = createFolder(`folder-${i}`, null);
        if (!folderId) continue;

        // 하위 폴더 생성
        for (let j = 0; j < SUB_FOLDER_COUNT; j++) {
            createFolder(`sub-${i}-${j}`, folderId);
        }

        // 파일 업로드
        for (let k = 0; k < FILE_COUNT; k++) {
            uploadFile(`file-${i}-${k}.txt`, folderId);
        }

        if ((i + 1) % 10 === 0) {
            console.log(`  Progress: ${i + 1}/${FOLDER_COUNT} folders created`);
        }
    }

    console.log('Seeding complete!');
}
