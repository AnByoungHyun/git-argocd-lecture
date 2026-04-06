'use strict';

const express = require('express');

const app = express();

const APP_NAME    = 'node-app';
const APP_VERSION = process.env.APP_VERSION || '1.1.0';
const APP_ENV     = process.env.APP_ENV     || 'production';
const PORT        = parseInt(process.env.PORT || '3000', 10);

// 앱 시작 시간 (페이지 하단 표시용)
const START_TIME = new Date().toISOString();

// ----------------------------------------------------------------
// Middleware
// ----------------------------------------------------------------
app.use(express.json());

// ----------------------------------------------------------------
// GET / — HTML 웹 페이지 (브라우저용, 배포 변경 시각화)
// ----------------------------------------------------------------
app.get('/', (req, res) => {
  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>node-app</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#68A063;color:#fff;font-family:system-ui,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center}
.card{background:rgba(0,0,0,.2);border-radius:16px;padding:2.5rem 3rem;text-align:center;max-width:480px;width:90%}
h1{font-size:2.8rem;margin-bottom:.75rem}
.version{display:inline-block;background:rgba(255,255,255,.25);border-radius:8px;padding:.3rem 1rem;font-size:1.3rem;font-weight:700;margin-bottom:1.5rem;letter-spacing:1px}
.info{font-size:1rem;line-height:2.2;opacity:.95}
.dot{color:#7fff7f;font-size:1.1rem}
.footer{margin-top:1.5rem;font-size:.75rem;opacity:.6;border-top:1px solid rgba(255,255,255,.2);padding-top:.75rem}
</style>
</head>
<body>
<div class="card">
  <h1>&#x1F7E2; node-app</h1>
  <div class="version">v${APP_VERSION}</div>
  <div class="info">
    <span class="dot">&#9679;</span> Running<br>
    Framework: Express<br>
    Port: ${PORT}<br>
    Environment: ${APP_ENV}
  </div>
  <div class="footer">Started: ${START_TIME}</div>
</div>
</body>
</html>`;
  res.type('html').send(html);
});

// ----------------------------------------------------------------
// GET /api — JSON 응답 (기존 GET / 응답 이동, API 클라이언트용)
// ----------------------------------------------------------------
app.get('/api', (req, res) => {
  res.status(200).json({
    app:         APP_NAME,
    version:     APP_VERSION,
    language:    'Node.js',
    framework:   'Express',
    port:        PORT,
    environment: APP_ENV,
  });
});

// ----------------------------------------------------------------
// GET /health — 헬스체크 (K8s liveness/readiness probe)
// ----------------------------------------------------------------
app.get('/health', (req, res) => {
  res.status(200).json({
    status:  'ok',
    app:     APP_NAME,
    version: APP_VERSION,
  });
});

// ----------------------------------------------------------------
// 405 — 등록된 경로에 허용되지 않는 메서드
// (404 catch-all 보다 반드시 먼저 선언)
// ----------------------------------------------------------------
app.all('/', (req, res) => {
  res.status(405).json({
    status:  'error',
    code:    405,
    message: 'Method Not Allowed',
    path:    req.path,
  });
});

app.all('/api', (req, res) => {
  res.status(405).json({
    status:  'error',
    code:    405,
    message: 'Method Not Allowed',
    path:    req.path,
  });
});

// ----------------------------------------------------------------
// 404 — 존재하지 않는 경로 (catch-all, 반드시 마지막)
// ----------------------------------------------------------------
app.use((req, res) => {
  res.status(404).json({
    status:  'error',
    code:    404,
    message: 'Not Found',
    path:    req.path,
  });
});

// ----------------------------------------------------------------
// 500 — 전역 에러 핸들러
// ----------------------------------------------------------------
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, _next) => {
  console.error(err);
  res.status(500).json({
    status:  'error',
    code:    500,
    message: 'Internal Server Error',
    path:    req.path,
  });
});

module.exports = { app, PORT };
