import http from 'k6/http';
import { check } from 'k6';

// 대량 데이터 시드 스크립트 (1회 실행, 다중 owner)
// k6 run perf/seed-data.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_COUNT = parseInt(__ENV.OWNER_COUNT || '5');
const FOLDER_COUNT = 100;      // owner당 루트 폴더 수
const SUB_FOLDER_COUNT = 10;   // 폴더당 하위 폴더 수
const FILE_COUNT = 10;         // 폴더당 파일 수

// init 단계에서 fixtures 로드 (VU 간 공유, 1회 실행)
const files1KB = [];
for (let i = 1; i <= 10; i++) {
    files1KB.push(open(`fixtures/1KB_${i}.bin`, 'b'));
}

function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export const options = {
    vus: 1,
    iterations: 1,
};

function createFolder(name, parentId, ownerId) {
    const body = parentId
        ? JSON.stringify({ name, parentId })
        : JSON.stringify({ name });

    const res = http.post(`${BASE_URL}/folders`, body, {
        headers: {
            'Content-Type': 'application/json',
            'X-OWNER-ID': String(ownerId),
        },
    });

    if (res.status === 200) {
        return JSON.parse(res.body).id;
    }
    console.error(`Failed to create folder: ${name}, status: ${res.status}`);
    return null;
}

function uploadFile(name, parentId, ownerId) {
    const content = pickRandom(files1KB);
    const payload = parentId
        ? JSON.stringify({ parentId, itemType: 'FILE' })
        : JSON.stringify({ itemType: 'FILE' });

    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(content, name, 'application/octet-stream'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, {
        headers: { 'X-OWNER-ID': String(ownerId) },
    });

    check(res, { 'file uploaded': (r) => r.status === 200 });
}

export default function () {
    for (let ownerId = 1; ownerId <= OWNER_COUNT; ownerId++) {
        console.log(`Seeding owner ${ownerId}: ${FOLDER_COUNT} folders with ${SUB_FOLDER_COUNT} subfolders and ${FILE_COUNT} files each...`);

        for (let i = 0; i < FOLDER_COUNT; i++) {
            const folderId = createFolder(`folder-${ownerId}-${i}`, null, ownerId);
            if (!folderId) continue;

            // 하위 폴더 생성
            for (let j = 0; j < SUB_FOLDER_COUNT; j++) {
                createFolder(`sub-${ownerId}-${i}-${j}`, folderId, ownerId);
            }

            // 파일 업로드
            for (let k = 0; k < FILE_COUNT; k++) {
                uploadFile(`file-${ownerId}-${i}-${k}.bin`, folderId, ownerId);
            }

            if ((i + 1) % 10 === 0) {
                console.log(`  Owner ${ownerId} progress: ${i + 1}/${FOLDER_COUNT} folders created`);
            }
        }
    }

    console.log(`Seeding complete! ${OWNER_COUNT} owners seeded.`);
}
