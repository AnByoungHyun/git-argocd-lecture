'use strict';

/**
 * Node.js built-in test runner (node:test) + supertest
 * 실행: node --test test/
 */
const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');

let request;
let server;
let app;

// supertest 동적 임포트 처리 (ESM 호환)
before(async () => {
  const supertest = require('supertest');
  ({ app } = require('../src/app'));
  request = supertest(app);
});

// ----------------------------------------------------------------
// GET /
// ----------------------------------------------------------------
describe('GET /', () => {
  test('should return 200 with app info', async () => {
    const res = await request.get('/');
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.app, 'node-app');
    assert.strictEqual(res.body.language, 'Node.js');
    assert.strictEqual(res.body.framework, 'Express');
    assert.strictEqual(typeof res.body.version, 'string');
    assert.strictEqual(typeof res.body.port, 'number');
    assert.ok(res.body.environment);
  });
});

// ----------------------------------------------------------------
// GET /health
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
// 404 — 알 수 없는 경로
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

// ----------------------------------------------------------------
// 405 — 허용되지 않는 메서드
// ----------------------------------------------------------------
describe('Method Not Allowed', () => {
  test('POST /health should return 404 (not registered)', async () => {
    const res = await request.post('/unknown');
    assert.strictEqual(res.status, 404);
    assert.strictEqual(res.body.status, 'error');
  });
});
