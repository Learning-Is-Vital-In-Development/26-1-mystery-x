import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 부하 테스트 메인 스크립트
// k6 run perf/load-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_ID = '1';
const HEADERS = { 'Content-Type': 'application/json', 'X-OWNER-ID': OWNER_ID };

// Custom metrics
const listDuration = new Trend('list_folder_duration', true);
const createDuration = new Trend('create_folder_duration', true);
const errorRate = new Rate('errors');

export const options = {
    scenarios: {
        // 시나리오 1: 읽기 부하 (폴더 목록 조회)
        read_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 20 },
                { duration: '30s', target: 50 },
                { duration: '10s', target: 0 },
            ],
            exec: 'readScenario',
        },
        // 시나리오 2: 쓰기 부하 (폴더 생성)
        write_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 5 },
                { duration: '30s', target: 20 },
                { duration: '10s', target: 0 },
            ],
            exec: 'writeScenario',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],  // 95% 요청이 500ms 이내
        errors: ['rate<0.1'],              // 에러율 10% 미만
    },
};

// 읽기 시나리오: 루트 폴더 목록 + 개별 폴더 조회
export function readScenario() {
    group('list root items', () => {
        const res = http.get(`${BASE_URL}/folders/root/items`, { headers: HEADERS });
        const ok = check(res, { 'root list 200': (r) => r.status === 200 });
        errorRate.add(!ok);
        listDuration.add(res.timings.duration);
    });

    // 랜덤 폴더 조회 (1~100 범위)
    group('list folder items', () => {
        const folderId = Math.floor(Math.random() * 100) + 1;
        const res = http.get(`${BASE_URL}/folders/${folderId}/items`, { headers: HEADERS });
        const ok = check(res, { 'folder list 2xx': (r) => r.status >= 200 && r.status < 300 });
        errorRate.add(!ok);
        listDuration.add(res.timings.duration);
    });

    sleep(0.5);
}

// 쓰기 시나리오: 폴더 생성
export function writeScenario() {
    group('create folder', () => {
        const name = `perf-folder-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        const res = http.post(`${BASE_URL}/folders`, JSON.stringify({ name }), { headers: HEADERS });
        const ok = check(res, { 'create 200': (r) => r.status === 200 });
        errorRate.add(!ok);
        createDuration.add(res.timings.duration);
    });

    sleep(1);
}
