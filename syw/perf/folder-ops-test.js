import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// 폴더 작업 성능 테스트 (삭제, 이동, 복사)
// k6 run perf/folder-ops-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_COUNT = parseInt(__ENV.OWNER_COUNT || '5');

const deleteDuration = new Trend('delete_folder_duration', true);
const moveDuration = new Trend('move_item_duration', true);
const copyDuration = new Trend('copy_item_duration', true);

// init 단계에서 fixtures 로드 (VU 간 공유, 1회 실행)
const files1KB = [];
for (let i = 1; i <= 10; i++) {
    files1KB.push(open(`fixtures/1KB_${i}.bin`, 'b'));
}

function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export const options = {
    scenarios: {
        folder_ops: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 10,
            exec: 'folderOpsScenario',
        },
    },
    thresholds: {
        delete_folder_duration: ['p(95)<2000'],
        move_item_duration: ['p(95)<500'],
        copy_item_duration: ['p(95)<2000'],
    },
};

function createFolder(name, parentId, headers) {
    const body = parentId
        ? JSON.stringify({ name, parentId })
        : JSON.stringify({ name });
    const res = http.post(`${BASE_URL}/folders`, body, { headers });
    if (res.status === 200) return JSON.parse(res.body).id;
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
    }, { headers: { 'X-OWNER-ID': String(ownerId) } });
    if (res.status === 200) return true;
    return false;
}

export function folderOpsScenario() {
    const ownerId = (__VU % OWNER_COUNT) + 1;
    const headers = { 'Content-Type': 'application/json', 'X-OWNER-ID': String(ownerId) };
    const tag = `${__VU}-${__ITER}-${Date.now()}`;

    // Setup: 폴더 트리 생성 (부모 > 자식 3개 + 파일 5개)
    const parentId = createFolder(`ops-parent-${tag}`, null, headers);
    if (!parentId) return;

    const childIds = [];
    for (let i = 0; i < 3; i++) {
        const childId = createFolder(`ops-child-${tag}-${i}`, parentId, headers);
        if (childId) childIds.push(childId);
    }
    for (let i = 0; i < 5; i++) {
        uploadFile(`ops-file-${tag}-${i}.bin`, parentId, ownerId);
    }

    // 이동 대상 폴더
    const moveTargetId = createFolder(`ops-move-target-${tag}`, null, headers);

    // 1) 복사 테스트
    if (childIds.length > 0) {
        group('copy folder', () => {
            const res = http.post(
                `${BASE_URL}/storage-items/${childIds[0]}/copy`,
                JSON.stringify({ targetParentId: moveTargetId }),
                { headers }
            );
            check(res, { 'copy ok': (r) => r.status === 200 });
            copyDuration.add(res.timings.duration);
        });
    }

    // 2) 이동 테스트
    if (childIds.length > 1) {
        group('move item', () => {
            const res = http.patch(
                `${BASE_URL}/storage-items/${childIds[1]}/move`,
                JSON.stringify({ targetParentId: moveTargetId }),
                { headers }
            );
            check(res, { 'move ok': (r) => r.status === 200 });
            moveDuration.add(res.timings.duration);
        });
    }

    // 3) 폴더 삭제 테스트 (하위 항목 포함 BFS 삭제)
    group('delete folder', () => {
        const res = http.del(`${BASE_URL}/folders/${parentId}`, null, { headers });
        check(res, { 'delete ok': (r) => r.status === 204 });
        deleteDuration.add(res.timings.duration);
    });

    // 정리
    if (moveTargetId) {
        http.del(`${BASE_URL}/folders/${moveTargetId}`, null, { headers });
    }

    sleep(0.5);
}
