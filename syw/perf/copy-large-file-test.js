import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 대용량 파일 복사 성능 테스트
// 파일 크기별로 업로드 후 복사하여 물리 파일 복제 vs 참조 공유 성능 차이를 측정
// k6 run -e BASE_URL=http://localhost:8081 perf/copy-large-file-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_COUNT = parseInt(__ENV.OWNER_COUNT || '5');

// 크기별 커스텀 메트릭
const copy1KB = new Trend('copy_1KB', true);
const copy100KB = new Trend('copy_100KB', true);
const copy1MB = new Trend('copy_1MB', true);
const copy5MB = new Trend('copy_5MB', true);
const errorRate = new Rate('errors');

// init 단계에서 fixtures 로드
const files1KB = [];
const files100KB = [];
const files1MB = [];
const files5MB = [];

for (let i = 1; i <= 10; i++) {
    files1KB.push(open(`fixtures/1KB_${i}.bin`, 'b'));
    files100KB.push(open(`fixtures/100KB_${i}.bin`, 'b'));
    files1MB.push(open(`fixtures/1MB_${i}.bin`, 'b'));
}
for (let i = 1; i <= 3; i++) {
    files5MB.push(open(`fixtures/5MB_${i}.bin`, 'b'));
}

function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export const options = {
    scenarios: {
        copy_1kb: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 20,
            exec: 'copy1KBFile',
        },
        copy_100kb: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 20,
            startTime: '15s',
            exec: 'copy100KBFile',
        },
        copy_1mb: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 10,
            startTime: '30s',
            exec: 'copy1MBFile',
        },
        copy_5mb: {
            executor: 'per-vu-iterations',
            vus: 3,
            iterations: 10,
            startTime: '45s',
            exec: 'copy5MBFile',
        },
    },
    thresholds: {
        copy_1KB: ['p(95)<1000'],
        copy_100KB: ['p(95)<2000'],
        copy_1MB: ['p(95)<3000'],
        copy_5MB: ['p(95)<5000'],
        errors: ['rate<0.05'],
    },
};

function uploadFileAndGetId(fileContent, fileName, parentId, ownerId) {
    const payload = parentId
        ? JSON.stringify({ parentId, itemType: 'FILE' })
        : JSON.stringify({ itemType: 'FILE' });

    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(fileContent, fileName, 'application/octet-stream'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, { headers: { 'X-OWNER-ID': String(ownerId) } });

    if (res.status !== 200) return null;

    // 업로드 API가 빈 body를 반환하므로, 루트 폴더 아이템 조회로 ID를 찾음
    const parentPath = parentId ? `${BASE_URL}/folders/${parentId}/items` : `${BASE_URL}/folders/root/items`;
    const listRes = http.get(parentPath, {
        headers: { 'Content-Type': 'application/json', 'X-OWNER-ID': String(ownerId) },
    });

    if (listRes.status === 200) {
        const items = JSON.parse(listRes.body);
        const found = items.find(i => i.displayName === fileName && i.itemType === 'FILE');
        if (found) return found.id;
    }
    return null;
}

function createFolder(name, ownerId) {
    const res = http.post(`${BASE_URL}/folders`, JSON.stringify({ name }), {
        headers: { 'Content-Type': 'application/json', 'X-OWNER-ID': String(ownerId) },
    });
    if (res.status === 200) return JSON.parse(res.body).id;
    return null;
}

function doCopyTest(files, sizeLabel, metric) {
    const ownerId = (__VU % OWNER_COUNT) + 1;
    const headers = { 'Content-Type': 'application/json', 'X-OWNER-ID': String(ownerId) };
    const tag = `${__VU}-${__ITER}-${Date.now()}`;

    // 1) 파일 업로드
    const fileContent = pickRandom(files);
    const fileName = `copy-test-${sizeLabel}-${tag}.bin`;
    const fileId = uploadFileAndGetId(fileContent, fileName, null, ownerId);

    if (!fileId) {
        errorRate.add(true);
        return;
    }

    // 2) 복사 대상 폴더 생성
    const targetFolderId = createFolder(`copy-target-${sizeLabel}-${tag}`, ownerId);
    if (!targetFolderId) {
        errorRate.add(true);
        return;
    }

    // 3) 파일 복사 (핵심 측정 구간)
    group(`copy ${sizeLabel} file`, () => {
        const res = http.post(
            `${BASE_URL}/storage-items/${fileId}/copy`,
            JSON.stringify({ targetParentId: targetFolderId }),
            { headers }
        );
        const ok = check(res, { [`copy ${sizeLabel} ok`]: (r) => r.status === 200 });
        errorRate.add(!ok);
        metric.add(res.timings.duration);
    });

    // 4) 정리
    http.del(`${BASE_URL}/folders/${targetFolderId}`, null, { headers });

    sleep(0.3);
}

export function copy1KBFile() { doCopyTest(files1KB, '1KB', copy1KB); }
export function copy100KBFile() { doCopyTest(files100KB, '100KB', copy100KB); }
export function copy1MBFile() { doCopyTest(files1MB, '1MB', copy1MB); }
export function copy5MBFile() { doCopyTest(files5MB, '5MB', copy5MB); }
