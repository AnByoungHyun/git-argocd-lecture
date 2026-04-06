'use strict';

/**
 * Node.js built-in test runner (node:test) + supertest
 * 실행: node --test test/
 */
const { test, describe, before } = require('node:test');
const assert = require('node:assert/strict');

let request;

before(async () => {
  const supertest = require('supertest');
  const { app } = require('../src/app');
  request = supertest(app);
});

// ----------------------------------------------------------------
// GET / — HTML 웹 페이지 테스트 (신규)
// ----------------------------------------------------------------
describe('GET /', () => {
  test('should return 200 with text/html', async () => {
    const res = await request.get('/');
    assert.strictEqual(res.status, 200);
    assert.ok(
      res.headers['content-type'].includes('text/html'),
      `expected text/html, got: ${res.headers['content-type']}`
    );
  });

  test('should contain app name and Running status in HTML', async () => {
    const res = await request.get('/');
    assert.ok(res.text.includes('node-app'), 'HTML should contain "node-app"');
    assert.ok(res.text.includes('Running'),  'HTML should contain "Running"');
    assert.ok(res.text.includes('Express'),  'HTML should contain "Express"');
    assert.ok(res.text.includes('#68A063'),  'HTML should contain node-app color');
  });

  test('POST / should return 405', async () => {
    const res = await request.post('/');
    assert.strictEqual(res.status, 405);
    assert.strictEqual(res.body.status, 'error');
    assert.strictEqual(res.body.code, 405);
  });
});

// ----------------------------------------------------------------
// GET /api — JSON 응답 테스트 (GET / 에서 이동)
// ----------------------------------------------------------------
describe('GET /api', () => {
  test('should return 200 with JSON app info', async () => {
    const res = await request.get('/api');
    assert.strictEqual(res.status, 200);
    assert.ok(res.headers['content-type'].includes('application/json'));
    assert.strictEqual(res.body.app, 'node-app');
    assert.strictEqual(res.body.language, 'Node.js');
    assert.strictEqual(res.body.framework, 'Express');
    assert.strictEqual(typeof res.body.version, 'string');
    assert.strictEqual(typeof res.body.port, 'number');
    assert.ok(res.body.environment);
  });
});

// ----------------------------------------------------------------
// GET /health — 헬스체크 테스트 (변경 없음)
// ----------------------------------------------------------------
describe('GET /health', () => {
  test('should return 200 with status ok', async () => {
    const res = await request.get('/health');
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.status, 'ok');
    assert.strictEqual(res.body.app, 'node-app');
    assert.ok(res.body.version);
  });
});

// ----------------------------------------------------------------
// 404 — 존재하지 않는 경로 테스트 (변경 없음)
// ----------------------------------------------------------------
describe('Unknown path', () => {
  test('should return 404 with error format', async () => {
    const res = await request.get('/unknown-path');
    assert.strictEqual(res.status, 404);
    assert.strictEqual(res.body.status, 'error');
    assert.strictEqual(res.body.code, 404);
    assert.strictEqual(res.body.message, 'Not Found');
    assert.strictEqual(res.body.path, '/unknown-path');
  });
});
