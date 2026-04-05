import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('error_rate');
const uploadDuration = new Trend('upload_duration');

export const options = {
    scenarios: {
        upload_load: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<30000'],
        error_rate: ['rate<0.1'],
    },
};

// 10MB test file data
const FILE_SIZE = 10 * 1024 * 1024;
const fileData = new ArrayBuffer(FILE_SIZE);

export default function () {
    const userId = 1;
    const filename = `before-${__VU}-${__ITER}-${Date.now()}.bin`;
    const boundary = '----k6FormBoundary7MA4YWxkTrZu0gW';

    // Build multipart body manually
    const prefix = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: application/octet-stream\r\n\r\n`;
    const suffix = `\r\n--${boundary}--\r\n`;

    const body = new ArrayBuffer(prefix.length + FILE_SIZE + suffix.length);
    const view = new Uint8Array(body);

    // Write prefix
    for (let i = 0; i < prefix.length; i++) {
        view[i] = prefix.charCodeAt(i);
    }
    // File data is zeros (from ArrayBuffer default)
    // Write suffix
    const suffixStart = prefix.length + FILE_SIZE;
    for (let i = 0; i < suffix.length; i++) {
        view[suffixStart + i] = suffix.charCodeAt(i);
    }

    const BASE = __ENV.BASE_URL || 'http://localhost:8888';
    const res = http.post(`${BASE}/api/files`, body, {
        headers: {
            'X-User-Id': String(userId),
            'Content-Type': `multipart/form-data; boundary=${boundary}`,
        },
        timeout: '60s',
    });

    const success = check(res, {
        'status is 202': (r) => r.status === 202,
    });
    errorRate.add(!success);
    uploadDuration.add(res.timings.duration);
    sleep(1);
}
