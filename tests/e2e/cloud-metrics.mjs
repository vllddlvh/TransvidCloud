// Đo cloud qua API công khai; chỉ lưu timing, mã HTTP và ID của dữ liệu test.
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

const base = new URL(process.argv[2] ?? 'https://vbeeaistt.com');
const output = resolve(process.argv[3] ?? 'local/e2e-logs/cloud-metrics');
const sampleCount = Number(process.env.CLOUD_METRIC_SAMPLES ?? 30);
const jobCount = Number(process.env.CLOUD_METRIC_JOBS ?? 12);
assert(Number.isInteger(sampleCount) && sampleCount > 0 && sampleCount <= 100);
assert(Number.isInteger(jobCount) && jobCount > 0 && jobCount <= 30);
await mkdir(output, { recursive: true });
const report = { startedAt: new Date().toISOString(), base: base.origin,
  method: { sampleCount, jobCount, uploadConcurrency: 2, confirmConcurrency: 2,
    pollConcurrency: 4, pollIntervalMs: 1000, percentile: 'nearest-rank',
    latency: 'client wall time through complete response body; includes network',
    workload: 'copies of sample-10s-720p.mp4; one batch, current AWS worker count unchanged' },
  requests: [], jobs: [], edgeCases: [], assetChecks: [], checks: [], errors: [] };
let token;
const round = value => Math.round(value * 100) / 100;
const sleep = ms => new Promise(r => setTimeout(r, ms));
function statistics(values) {
  if (!values.length) return null;
  const a = [...values].sort((x, y) => x - y);
  const p = q => a[Math.max(0, Math.ceil(a.length * q) - 1)];
  return { n: a.length, min: round(a[0]), mean: round(a.reduce((s, x) => s + x, 0) / a.length),
    p50: round(p(.5)), p95: round(p(.95)), max: round(a.at(-1)) };
}
async function request(label, url, options = {}, expected = [200], record = true) {
  const started = performance.now();
  const row = { label, at: new Date().toISOString(), method: options.method ?? 'GET' };
  try {
    const response = await fetch(new URL(url, base), { ...options,
      signal: AbortSignal.timeout(options.method === 'PUT' ? 60000 : 30000) });
    const data = Buffer.from(await response.arrayBuffer());
    Object.assign(row, { status: response.status, durationMs: round(performance.now() - started),
      bytes: data.length, expected: expected.includes(response.status),
      contentType: response.headers.get('content-type'), cache: response.headers.get('x-cache'),
      age: response.headers.get('age') });
    if (record) report.requests.push(row);
    assert(row.expected, `${label}: HTTP ${response.status}`);
    return { response, data, row };
  } catch (e) {
    if (!('status' in row)) {
      Object.assign(row, { status: null, expected: false, durationMs: round(performance.now() - started), error: e.name });
      if (record) report.requests.push(row);
    }
    throw new Error(`${label}: ${e.name}${row.status ? ' HTTP ' + row.status : ''}`);
  }
}
async function api(path, method = 'GET', body, label = `${method} /api${path}`, expected = [200]) {
  const result = await request(label, '/api' + path, { method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) }, expected);
  return { ...result, json: result.data.length ? JSON.parse(result.data) : null };
}
async function pool(items, concurrency, fn) {
  const result = new Array(items.length);
  let next = 0;
  const errors = [];
  await Promise.all(Array.from({ length: Math.min(items.length, concurrency) }, async () => {
    for (;;) {
      const i = next++;
      if (i >= items.length) return;
      try { result[i] = await fn(items[i], i); } catch (e) { errors.push(e); }
    }
  }));
  if (errors.length) throw errors[0];
  return result;
}
async function createUpload(projectId, filename, kind) {
  const sample = await readFile(new URL('../../worker/testdata/' + filename, import.meta.url));
  const { json: ticket } = await api(`/projects/${projectId}/videos`, 'POST', {
    title: `Report ${kind}`, filename, sizeBytes: sample.length, contentType: 'video/mp4'
  }, 'POST /api/projects/:id/videos', [200, 201]);
  const upload = await request('PUT S3 sample', ticket.upload.presignedUrl, {
    method: 'PUT', headers: ticket.upload.requiredHeaders, body: sample
  }, [200, 204]);
  return { videoId: ticket.video.videoId, projectId, kind, filename, sizeBytes: sample.length,
    uploadMs: upload.row.durationMs };
}
async function confirm(job) {
  job.confirmStartedAt = new Date().toISOString();
  const response = await api(`/videos/${job.videoId}/confirm`, 'POST', undefined, 'POST /api/videos/:id/confirm', [200, 202]);
  job.confirmAcceptedAt = new Date().toISOString();
  job.confirmMs = response.row.durationMs;
}
async function waitForJobs(jobs, expectedStatus = 'COMPLETED') {
  const deadline = Date.now() + 240000;
  while (Date.now() < deadline) {
    await pool(jobs.filter(j => !j.observedAt), 4, async job => {
      const { json: video } = await api(`/videos/${job.videoId}`, 'GET', undefined, 'GET /api/videos/:id polling');
      if (video.status === expectedStatus) {
        Object.assign(job, { status: video.status, observedAt: new Date().toISOString(),
          assets: video.assets, errorCode: video.errorCode ?? null,
          startedAt: video.startedAt ?? null, completedAt: video.completedAt ?? null,
          attemptCount: video.attemptCount ?? null });
        job.confirmToObservedMs = Date.parse(job.observedAt) - Date.parse(job.confirmStartedAt);
      } else if (video.status.startsWith('FAILED')) {
        throw new Error(`Video ${job.videoId}: unexpected ${video.status}`);
      }
    });
    if (jobs.every(j => j.observedAt)) return;
    await sleep(1000);
  }
  throw new Error('Video completion timeout');
}
async function checkAssets(job, detailed = false) {
  const master = await request('GET media master first', job.assets.masterPlaylistUrl);
  assert(master.row.contentType?.includes('application/vnd.apple.mpegurl'));
  const thumbnail = await request('GET media thumbnail', job.assets.thumbnailUrl);
  assert(thumbnail.row.contentType?.includes('image/jpeg'));
  const variants = master.data.toString().split(/\r?\n/).filter(l => l && !l.startsWith('#'));
  const check = { videoId: job.videoId, kind: job.kind, variants, segmentCount: 0 };
  if (detailed) {
    await request('GET media master repeat', job.assets.masterPlaylistUrl);
    for (const variant of variants) {
      const playlistUrl = new URL(variant, new URL(job.assets.masterPlaylistUrl, base));
      const playlist = await request('GET media rendition first', playlistUrl);
      assert(playlist.row.contentType?.includes('application/vnd.apple.mpegurl'));
      const segments = playlist.data.toString().split(/\r?\n/).filter(l => l && !l.startsWith('#'));
      assert(segments.length > 0);
      await request('GET media rendition repeat', playlistUrl);
      for (const segment of segments) {
        const segmentUrl = new URL(segment, playlistUrl);
        const first = await request('GET media segment first', segmentUrl);
        assert(first.row.contentType?.includes('video/mp2t') && first.data.length > 0);
        await request('GET media segment repeat', segmentUrl);
        check.segmentCount++;
      }
    }
  }
  report.assetChecks.push(check);
}
try {
  const source = await readFile(new URL('../../worker/testdata/sample-10s-720p.mp4', import.meta.url));
  report.dataset = { filename: 'sample-10s-720p.mp4', sizeBytes: source.length,
    sha256: createHash('sha256').update(source).digest('hex') };
  for (let i = 0; i < 3; i++) await request('warmup', '/api/actuator/health', {}, [200], false);
  for (const path of ['/', '/login']) {
    const r = await request(`GET ${path}`, path);
    assert(r.data.toString().includes('<div id="root">'));
  }
  await request('GET projects anonymous', '/api/projects', {}, [401]);
  const stamp = Date.now();
  const email = `cloud-report-${stamp}@transvidcloud.test`;
  const password = randomBytes(24).toString('base64url');
  const auth = await api('/auth/register', 'POST', { email, password, fullName: 'Cloud Report' }, 'POST /api/auth/register', [200, 201]);
  token = auth.json.accessToken;
  assert(token);
  const project = (await api('/projects', 'POST', { name: `Report ${stamp}`, description: 'Measured AWS pipeline; synthetic test data' }, 'POST /api/projects', [200, 201])).json;
  report.projectId = project.projectId;
  for (const path of ['/actuator/health', '/auth/me', '/projects', `/projects/${project.projectId}`]) {
    const label = path.startsWith('/projects/') ? 'GET /api/projects/:id sample' : `GET /api${path} sample`;
    for (let i = 0; i < sampleCount; i++) await api(path, 'GET', undefined, label);
  }
  for (let i = 0; i < sampleCount; i++) {
    const login = await api('/auth/login', 'POST', { email, password }, 'POST /api/auth/login sample');
    assert(login.json.accessToken);
  }
  await api('/auth/login', 'POST', { email, password: 'wrong-password-for-report' }, 'POST login invalid password', [401]);
  console.log('PASS public routes, auth, and API latency samples');
  report.jobs = await pool(Array.from({ length: jobCount }, (_, i) => i), 2,
    async i => createUpload(project.projectId, 'sample-10s-720p.mp4', `batch-${i + 1}`));
  report.batchStartedAt = new Date().toISOString();
  await pool(report.jobs, 2, confirm);
  await waitForJobs(report.jobs);
  report.batchFinishedAt = new Date().toISOString();
  for (let i = 0; i < report.jobs.length; i++) await checkAssets(report.jobs[i], i === 0);
  console.log(`PASS ${jobCount}/${jobCount} batch videos COMPLETED`);
  for (const filename of ['sample-360p.mp4', 'sample-no-audio.mp4']) {
    const job = await createUpload(project.projectId, filename, filename);
    report.edgeCases.push(job);
    await confirm(job);
    await waitForJobs([job]);
    await checkAssets(job, true);
  }
  const invalid = await createUpload(project.projectId, 'fake-video.mp4', 'invalid-video');
  report.edgeCases.push(invalid);
  await confirm(invalid);
  await waitForJobs([invalid], 'FAILED_VALIDATION');
  assert.equal(invalid.errorCode, 'NOT_A_VIDEO');
  await api(`/videos/${invalid.videoId}`, 'DELETE', undefined, 'DELETE invalid video', [204]);
  report.checks.push({ name: 'invalid video -> FAILED_VALIDATION / NOT_A_VIDEO -> deleted', pass: true });
  const missing = await request('GET missing media', '/media/__report_missing__.m3u8', {}, [403, 404]);
  assert(!missing.row.contentType?.includes('text/html'), 'Media error must not return SPA HTML');
  report.checks.push({ name: '360p, no-audio, HLS segments, thumbnail, media-error isolation', pass: true });
  report.pass = true;
} catch (e) {
  report.pass = false;
  report.errors.push(e.message);
  process.exitCode = 1;
} finally {
  report.finishedAt = new Date().toISOString();
  report.totalDurationMs = Date.parse(report.finishedAt) - Date.parse(report.startedAt);
  report.http5xx = report.requests.filter(r => r.status >= 500).length;
  report.unexpectedResponses = report.requests.filter(r => !r.expected).length;
  report.latencyByLabel = Object.fromEntries([...new Set(report.requests.map(r => r.label))].map(label => {
    const rows = report.requests.filter(r => r.label === label);
    return [label, { ...statistics(rows.map(r => r.durationMs)), expectedResponses: rows.filter(r => r.expected).length }];
  }));
  const completed = report.jobs.filter(j => j.observedAt);
  report.batchSummary = { completed: completed.length, submitted: report.jobs.length,
    uploadMs: statistics(report.jobs.map(j => j.uploadMs)),
    confirmToObservedMs: statistics(completed.map(j => j.confirmToObservedMs)) };
  if (report.batchFinishedAt) {
    report.batchSummary.wallSeconds = (Date.parse(report.batchFinishedAt) - Date.parse(report.batchStartedAt)) / 1000;
    report.batchSummary.observedJobsPerMinute = round(completed.length * 60 / report.batchSummary.wallSeconds);
  }
  await writeFile(resolve(output, 'cloud-metrics.json'), JSON.stringify(report, null, 2) + '\n');
  const csv = ['label,at,method,status,durationMs,bytes,expected,cache', ...report.requests.map(r =>
    [r.label,r.at,r.method,r.status,r.durationMs,r.bytes,r.expected,r.cache].map(v => JSON.stringify(v ?? '')).join(','))];
  await writeFile(resolve(output, 'http-samples.csv'), csv.join('\n') + '\n');
  console.log(JSON.stringify({ pass: report.pass, durationMs: report.totalDurationMs, requests: report.requests.length,
    http5xx: report.http5xx, batch: report.batchSummary, errors: report.errors }));
}
