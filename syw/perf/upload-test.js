import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 파일 크기별 업로드 부하 테스트
// k6 run perf/upload-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_ID = '1';

// 크기별 커스텀 메트릭
const upload1KB = new Trend('upload_1KB', true);
const upload100KB = new Trend('upload_100KB', true);
const upload1MB = new Trend('upload_1MB', true);
const upload5MB = new Trend('upload_5MB', true);
const upload10MB = new Trend('upload_10MB', true);
const errorRate = new Rate('errors');

export const options = {
    scenarios: {
        size_1kb: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 10 },
                { duration: '15s', target: 20 },
                { duration: '5s', target: 0 },
            ],
            exec: 'upload1KBFile',
        },
        size_100kb: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '25s',
            stages: [
                { duration: '5s', target: 10 },
                { duration: '15s', target: 20 },
                { duration: '5s', target: 0 },
            ],
            exec: 'upload100KBFile',
        },
        size_1mb: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '50s',
            stages: [
                { duration: '5s', target: 5 },
                { duration: '15s', target: 10 },
                { duration: '5s', target: 0 },
            ],
            exec: 'upload1MBFile',
        },
        size_5mb: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '75s',
            stages: [
                { duration: '5s', target: 3 },
                { duration: '15s', target: 5 },
                { duration: '5s', target: 0 },
            ],
            exec: 'upload5MBFile',
        },
        size_10mb: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '100s',
            stages: [
                { duration: '5s', target: 2 },
                { duration: '15s', target: 3 },
                { duration: '5s', target: 0 },
            ],
            exec: 'upload10MBFile',
        },
    },
    thresholds: {
        upload_1KB: ['p(95)<1000'],
        upload_100KB: ['p(95)<2000'],
        upload_1MB: ['p(95)<3000'],
        upload_5MB: ['p(95)<5000'],
        upload_10MB: ['p(95)<10000'],
        errors: ['rate<0.05'],
    },
};

function doUpload(sizeBytes, metric) {
    const tag = `${__VU}-${__ITER}-${Date.now()}`;
    const fileName = `perf-${sizeBytes}B-${tag}.txt`;
    const content = 'x'.repeat(sizeBytes);
    const payload = JSON.stringify({ itemType: 'FILE' });

    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(content, fileName, 'text/plain'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, {
        headers: { 'X-OWNER-ID': OWNER_ID },
    });

    const ok = check(res, { 'upload ok': (r) => r.status === 200 });
    errorRate.add(!ok);
    metric.add(res.timings.duration);
    sleep(0.5);
}

export function upload1KBFile() { doUpload(1 * 1024, upload1KB); }
export function upload100KBFile() { doUpload(100 * 1024, upload100KB); }
export function upload1MBFile() { doUpload(1024 * 1024, upload1MB); }
export function upload5MBFile() { doUpload(5 * 1024 * 1024, upload5MB); }
export function upload10MBFile() { doUpload(10 * 1024 * 1024, upload10MB); }
