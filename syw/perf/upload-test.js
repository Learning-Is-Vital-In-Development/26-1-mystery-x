import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 파일 크기별 업로드 부하 테스트
// k6 run perf/upload-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OWNER_COUNT = parseInt(__ENV.OWNER_COUNT || '5');

// 크기별 커스텀 메트릭
const upload1KB = new Trend('upload_1KB', true);
const upload100KB = new Trend('upload_100KB', true);
const upload1MB = new Trend('upload_1MB', true);
const upload5MB = new Trend('upload_5MB', true);
const upload10MB = new Trend('upload_10MB', true);
const errorRate = new Rate('errors');

// init 단계에서 fixtures 로드 (VU 간 공유, 1회 실행)
const files1KB = [];
const files100KB = [];
const files1MB = [];
const files5MB = [];
const files10MB = [];

for (let i = 1; i <= 10; i++) {
    files1KB.push(open(`fixtures/1KB_${i}.bin`, 'b'));
    files100KB.push(open(`fixtures/100KB_${i}.bin`, 'b'));
    files1MB.push(open(`fixtures/1MB_${i}.bin`, 'b'));
}
for (let i = 1; i <= 3; i++) {
    files5MB.push(open(`fixtures/5MB_${i}.bin`, 'b'));
    files10MB.push(open(`fixtures/10MB_${i}.bin`, 'b'));
}

function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

const sizeToFiles = {
    1024: files1KB,
    102400: files100KB,
    1048576: files1MB,
    5242880: files5MB,
    10485760: files10MB,
};

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
    const ownerId = (__VU % OWNER_COUNT) + 1;
    const tag = `${__VU}-${__ITER}-${Date.now()}`;
    const fileName = `perf-${sizeBytes}B-${tag}.bin`;
    const content = pickRandom(sizeToFiles[sizeBytes]);
    const payload = JSON.stringify({ itemType: 'FILE' });

    const res = http.post(`${BASE_URL}/storage-items`, {
        file: http.file(content, fileName, 'application/octet-stream'),
        payload: http.file(payload, 'payload.json', 'application/json'),
    }, {
        headers: { 'X-OWNER-ID': String(ownerId) },
    });

    const ok = check(res, { 'upload ok': (r) => r.status === 200 });
    errorRate.add(!ok);
    metric.add(res.timings.duration);
    sleep(0.5);
}

export function upload1KBFile() { doUpload(1024, upload1KB); }
export function upload100KBFile() { doUpload(102400, upload100KB); }
export function upload1MBFile() { doUpload(1048576, upload1MB); }
export function upload5MBFile() { doUpload(5242880, upload5MB); }
export function upload10MBFile() { doUpload(10485760, upload10MB); }
