import http from 'k6/http';
import { check, group } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// === Download metrics ===
const downloadDuration = new Trend('download_duration');
const downloadTTFB = new Trend('download_ttfb');
const downloadReceiving = new Trend('download_receiving');
const downloadThroughput = new Trend('download_throughput_mbps');
const downloadErrors = new Rate('download_errors');
const totalBytes = new Counter('total_bytes_received');

export const options = {
    scenarios: {
        // Scenario 1: 단일 사용자 다운로드 (baseline throughput)
        single_user: {
            executor: 'constant-vus',
            vus: 1,
            duration: '15s',
            tags: { scenario: 'single' },
        },
        // Scenario 2: 동시 다운로드 (5명)
        concurrent_5: {
            executor: 'constant-vus',
            vus: 5,
            duration: '15s',
            startTime: '20s',
            tags: { scenario: 'concurrent_5' },
        },
        // Scenario 3: 동시 다운로드 (20명)
        concurrent_20: {
            executor: 'constant-vus',
            vus: 20,
            duration: '15s',
            startTime: '40s',
            tags: { scenario: 'concurrent_20' },
        },
    },
    thresholds: {
        download_errors: ['rate<0.05'],
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8888';
const FILE_ID = __ENV.FILE_ID || '3';

export default function () {
    const res = http.get(`${BASE}/api/files/${FILE_ID}`, {
        headers: { 'X-User-Id': '1' },
        responseType: 'binary',
        timeout: '30s',
    });

    const ok = check(res, {
        'status 200': (r) => r.status === 200,
        'has content': (r) => r.body && r.body.byteLength > 0,
    });

    downloadErrors.add(!ok);

    if (ok) {
        const sizeMB = res.body.byteLength / (1024 * 1024);
        const durationSec = res.timings.duration / 1000;
        const throughputMbps = sizeMB / durationSec;

        downloadDuration.add(res.timings.duration);
        downloadTTFB.add(res.timings.waiting);
        downloadReceiving.add(res.timings.receiving);
        downloadThroughput.add(throughputMbps);
        totalBytes.add(res.body.byteLength);
    }
}
