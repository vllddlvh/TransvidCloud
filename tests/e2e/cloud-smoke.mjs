// Chạy: node tests/e2e/cloud-smoke.mjs https://<cloudfront-domain>
// Tạo tài khoản/project/video kiểm thử; không in JWT, password hoặc presigned URL.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
const base = new URL(process.argv[2] ?? 'https://dymj0klwmbp1k.cloudfront.net');
const apiBase = new URL(process.env.E2E_API_BASE_URL ?? base);
const sample = await readFile(process.env.E2E_SAMPLE_PATH ?? new URL('../../worker/testdata/sample-10s-720p.mp4', import.meta.url));
let token;
async function api(path, method = 'GET', body) {
  const response = await fetch(new URL('/api' + path, apiBase), {
    method, signal: AbortSignal.timeout(30000),
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
  assert(response.ok, method + ' ' + path + ': HTTP ' + response.status);
  return response.status === 204 ? null : response.json();
}
async function asset(path, type) {
  const response = await fetch(new URL(path, base), { signal: AbortSignal.timeout(30000) });
  assert.equal(response.status, 200, 'Asset HTTP: ' + path);
  assert(response.headers.get('content-type')?.includes(type), 'Content-Type: ' + path);
  return response;
}
try {
  for (const path of ['/', '/login']) {
    const response = await asset(path, 'text/html');
    assert((await response.text()).includes('<div id="root">'), 'Frontend React root');
  }
  assert.equal((await api('/actuator/health')).status, 'UP');
  const stamp = Date.now();
  const auth = await api('/auth/register', 'POST', {
    email: 'cloud-smoke-' + stamp + '@transvidcloud.test',
    password: randomBytes(24).toString('base64url'), fullName: 'Cloud Smoke'
  });
  token = auth.accessToken;
  assert(token, 'Registration returns accessToken');
  console.log('PASS frontend, health, registration');
  const project = await api('/projects', 'POST', { name: 'Cloud smoke ' + stamp, description: 'AWS runbook E2E' });
  const ticket = await api('/projects/' + project.projectId + '/videos', 'POST', {
    title: 'Cloud smoke 10s 720p', filename: 'sample-10s-720p.mp4', sizeBytes: sample.length, contentType: 'video/mp4'
  });
  const upload = await fetch(ticket.upload.presignedUrl, {
    method: 'PUT', headers: ticket.upload.requiredHeaders, body: sample, signal: AbortSignal.timeout(60000)
  });
  assert(upload.ok, 'S3 presigned PUT: HTTP ' + upload.status);
  const videoId = ticket.video.videoId;
  await api('/videos/' + videoId + '/confirm', 'POST');
  console.log('PASS project, presigned S3 PUT, confirm');
  let video;
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    video = await api('/videos/' + videoId);
    if (video.status === 'COMPLETED') break;
    assert(!video.status.startsWith('FAILED'), 'Video failed: ' + video.status + ', ' + video.errorCode);
    await new Promise(resolve => setTimeout(resolve, 3000));
  }
  assert.equal(video.status, 'COMPLETED', 'Video completes before timeout');
  const master = await asset(video.assets.masterPlaylistUrl, 'application/vnd.apple.mpegurl');
  const playlist = await master.text();
  for (const rendition of ['360p', '720p']) {
    assert(playlist.includes(rendition + '/index.m3u8'), 'Master contains ' + rendition);
    const renditionUrl = new URL(rendition + '/index.m3u8', new URL(video.assets.masterPlaylistUrl, base));
    const response = await asset(renditionUrl, 'application/vnd.apple.mpegurl');
    const segment = (await response.text()).split(/\r?\n/).find(line => line && !line.startsWith('#'));
    assert(segment, 'Playlist has segment');
    await (await asset(new URL(segment, renditionUrl), 'video/mp2t')).arrayBuffer();
  }
  await asset(video.assets.thumbnailUrl, 'image/jpeg');
  const missing = await fetch(new URL('/media/__missing__.m3u8', base));
  assert(missing.status >= 400 && !missing.headers.get('content-type')?.includes('text/html'), 'Media errors do not become SPA HTML');
  console.log('PASS worker COMPLETED, HLS 360p/720p segments, thumbnail, media error isolation');
  console.log(JSON.stringify({ base: base.origin, apiBase: apiBase.origin, projectId: project.projectId, videoId, status: video.status, masterPlaylistUrl: video.assets.masterPlaylistUrl }));
} catch (error) {
  console.error('FAIL cloud smoke: ' + error.message);
  process.exitCode = 1;
}
