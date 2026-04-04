'use strict';

const express = require('express');

const app = express();

const APP_NAME    = 'node-app';
const APP_VERSION = process.env.APP_VERSION || '1.0.0';
const APP_ENV     = process.env.APP_ENV     || 'production';
const PORT        = parseInt(process.env.PORT || '3000', 10);

// ----------------------------------------------------------------
// Middleware
// ----------------------------------------------------------------
app.use(express.json());

// ----------------------------------------------------------------
// GET / — 앱 기본 정보
// ----------------------------------------------------------------
app.get('/', (req, res) => {
  res.status(200).json({
    app:       APP_NAME,
    version:   APP_VERSION,
    language:  'Node.js',
    framework: 'Express',
    port:      PORT,
    environment: APP_ENV,
  });
});

// ----------------------------------------------------------------
// GET /health — 헬스체크
// ----------------------------------------------------------------
app.get('/health', (req, res) => {
  res.status(200).json({
    status:  'ok',
    app:     APP_NAME,
    version: APP_VERSION,
  });
});

// ----------------------------------------------------------------
// 404 — 존재하지 않는 경로
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
// 405 — 허용되지 않는 메서드 (POST /  등)
// ----------------------------------------------------------------
app.all('/', (req, res) => {
  res.status(405).json({
    status:  'error',
    code:    405,
    message: 'Method Not Allowed',
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
