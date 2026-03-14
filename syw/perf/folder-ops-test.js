import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// 폴더 작업 성능 테스트 (삭제, 이동, 복사)
// k6 run perf/folder-ops-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_ID = '1';
const HEADERS = { 'Content-Type': 'application/json', 'X-OWNER-ID': OWNER_ID };

const deleteDuration = new Trend('delete_folder_duration', true);
const moveDuration = new Trend('move_item_duration', true);
const copyDuration = new Trend('copy_item_duration', true);

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

function createFolder(name, parentId) {
    const body = parentId
        ? JSON.stringify({ name, parentId })
        : JSON.stringify({ name });
    const res = http.post(`${BASE_URL}/folders`, body, { headers: HEADERS });
    if (res.status === 200) return JSON.parse(res.body).id;
    return null;
}

function uploadFile(name, parentId) {
    const content = 'x'.repeat(1024);
    const payload = parentId
        ? JSON.stringify({ parentId, itemType: 'FILE' })
        : JSON.stringify({ itemType: 'FILE' });
    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(content, name, 'text/plain'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, { headers: { 'X-OWNER-ID': OWNER_ID } });
    if (res.status === 200) return true;
    return false;
}

export function folderOpsScenario() {
    const tag = `${__VU}-${__ITER}-${Date.now()}`;

    // Setup: 폴더 트리 생성 (부모 > 자식 3개 + 파일 5개)
    const parentId = createFolder(`ops-parent-${tag}`, null);
    if (!parentId) return;

    const childIds = [];
    for (let i = 0; i < 3; i++) {
        const childId = createFolder(`ops-child-${tag}-${i}`, parentId);
        if (childId) childIds.push(childId);
    }
    for (let i = 0; i < 5; i++) {
        uploadFile(`ops-file-${tag}-${i}.txt`, parentId);
    }

    // 이동 대상 폴더
    const moveTargetId = createFolder(`ops-move-target-${tag}`, null);

    // 1) 복사 테스트
    if (childIds.length > 0) {
        group('copy folder', () => {
            const res = http.post(
                `${BASE_URL}/storage-items/${childIds[0]}/copy`,
                JSON.stringify({ targetParentId: moveTargetId }),
                { headers: HEADERS }
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
                { headers: HEADERS }
            );
            check(res, { 'move ok': (r) => r.status === 200 });
            moveDuration.add(res.timings.duration);
        });
    }

    // 3) 폴더 삭제 테스트 (하위 항목 포함 BFS 삭제)
    group('delete folder', () => {
        const res = http.del(`${BASE_URL}/folders/${parentId}`, null, { headers: HEADERS });
        check(res, { 'delete ok': (r) => r.status === 204 });
        deleteDuration.add(res.timings.duration);
    });

    // 정리
    if (moveTargetId) {
        http.del(`${BASE_URL}/folders/${moveTargetId}`, null, { headers: HEADERS });
    }

    sleep(0.5);
}
