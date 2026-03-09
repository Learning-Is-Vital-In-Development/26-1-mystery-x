import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('error_rate');
const initDuration = new Trend('init_duration');
const uploadDuration = new Trend('upload_duration');
const totalDuration = new Trend('total_duration');

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
    const BASE = __ENV.BASE_URL || 'http://localhost:8888';
    const userId = 1;
    const start = Date.now();
    const filename = `after-${__VU}-${__ITER}-${Date.now()}.bin`;

    // Step 1: init (metadata only)
    const initRes = http.post(`${BASE}/api/files/init`,
        JSON.stringify({
            fileName: filename,
            fileSize: FILE_SIZE,
            contentType: 'application/octet-stream',
            folderId: null,
        }),
        {
            headers: {
                'X-User-Id': String(userId),
                'Content-Type': 'application/json',
            },
        }
    );
    initDuration.add(initRes.timings.duration);

    const initOk = check(initRes, { 'init status 200': (r) => r.status === 200 });
    if (!initOk) {
        errorRate.add(true);
        return;
    }

    const { metadataId, uploadToken } = initRes.json();

    // Step 2: upload to Nginx (raw body, no JVM involvement)
    const uploadRes = http.post(
        `${BASE}/upload/${metadataId}?token=${uploadToken}`,
        fileData,
        {
            headers: { 'Content-Type': 'application/octet-stream' },
            timeout: '60s',
        }
    );
    uploadDuration.add(uploadRes.timings.duration);

    const uploadOk = check(uploadRes, { 'upload status 200': (r) => r.status === 200 });
    errorRate.add(!uploadOk);
    totalDuration.add(Date.now() - start);
    sleep(1);
}
