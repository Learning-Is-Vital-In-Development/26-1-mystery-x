import http from 'k6/http';
import { check, group } from 'k6';
import { Trend } from 'k6/metrics';

// === 응답 시간 메트릭 (ms) ===
const apiGzipDuration = new Trend('api_gzip_duration');
const apiPlainDuration = new Trend('api_plain_duration');
const htmlGzipDuration = new Trend('html_gzip_duration');
const htmlPlainDuration = new Trend('html_plain_duration');

// === TTFB - 서버 처리 시간 (ms) ===
const apiGzipTTFB = new Trend('api_gzip_ttfb');
const apiPlainTTFB = new Trend('api_plain_ttfb');
const htmlGzipTTFB = new Trend('html_gzip_ttfb');
const htmlPlainTTFB = new Trend('html_plain_ttfb');

// === Receiving - 실제 네트워크 수신 시간 (ms) ===
// gzip 압축 시 전송량이 줄어 수신 시간이 짧아짐
const apiGzipRecv = new Trend('api_gzip_receiving');
const apiPlainRecv = new Trend('api_plain_receiving');
const htmlGzipRecv = new Trend('html_gzip_receiving');
const htmlPlainRecv = new Trend('html_plain_receiving');

// === 파일 다운로드 메트릭 ===
const fileDownloadDuration = new Trend('file_download_duration');
const fileDownloadRecv = new Trend('file_download_receiving');

export const options = {
  scenarios: {
    load_test: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = 'http://localhost:8888';
const HEADERS_GZIP = { 'X-User-Id': '1', 'Accept-Encoding': 'gzip, deflate' };
const HEADERS_PLAIN = { 'X-User-Id': '1', 'Accept-Encoding': 'identity' };

export default function () {
  // 1. API JSON - gzip
  group('API JSON (gzip)', () => {
    const res = http.get(`${BASE}/api/folders/2/contents`, { headers: HEADERS_GZIP });
    check(res, {
      'status 200': (r) => r.status === 200,
      'gzip enabled': (r) => (r.headers['Content-Encoding'] || '').includes('gzip'),
    });
    apiGzipDuration.add(res.timings.duration);
    apiGzipTTFB.add(res.timings.waiting);
    apiGzipRecv.add(res.timings.receiving);
  });

  // 2. API JSON - plain
  group('API JSON (plain)', () => {
    const res = http.get(`${BASE}/api/folders/2/contents`, { headers: HEADERS_PLAIN });
    check(res, {
      'status 200': (r) => r.status === 200,
      'no gzip': (r) => !(r.headers['Content-Encoding'] || '').includes('gzip'),
    });
    apiPlainDuration.add(res.timings.duration);
    apiPlainTTFB.add(res.timings.waiting);
    apiPlainRecv.add(res.timings.receiving);
  });

  // 3. UI HTML - gzip
  group('UI HTML (gzip)', () => {
    const res = http.get(`${BASE}/ui`, { headers: HEADERS_GZIP });
    check(res, {
      'status 200': (r) => r.status === 200,
      'gzip enabled': (r) => (r.headers['Content-Encoding'] || '').includes('gzip'),
    });
    htmlGzipDuration.add(res.timings.duration);
    htmlGzipTTFB.add(res.timings.waiting);
    htmlGzipRecv.add(res.timings.receiving);
  });

  // 4. UI HTML - plain
  group('UI HTML (plain)', () => {
    const res = http.get(`${BASE}/ui`, { headers: HEADERS_PLAIN });
    check(res, {
      'status 200': (r) => r.status === 200,
      'no gzip': (r) => !(r.headers['Content-Encoding'] || '').includes('gzip'),
    });
    htmlPlainDuration.add(res.timings.duration);
    htmlPlainTTFB.add(res.timings.waiting);
    htmlPlainRecv.add(res.timings.receiving);
  });

  // 5. File download (11MB PDF) - 10% 확률
  if (Math.random() < 0.1) {
    group('File Download (PDF 11MB)', () => {
      const res = http.get(`${BASE}/api/files/5`, {
        headers: HEADERS_PLAIN,
        responseType: 'binary',
      });
      check(res, { 'status 200': (r) => r.status === 200 });
      fileDownloadDuration.add(res.timings.duration);
      fileDownloadRecv.add(res.timings.receiving);
    });
  }
}
