'use strict';

const { app, PORT } = require('./app');

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log(`[node-app] listening on port ${PORT}`);
  console.log(`[node-app] version: ${process.env.APP_VERSION || '1.0.0'}`);
  console.log(`[node-app] environment: ${process.env.APP_ENV || 'production'}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('[node-app] SIGTERM received — shutting down gracefully');
  server.close(() => {
    console.log('[node-app] server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('[node-app] SIGINT received — shutting down gracefully');
  server.close(() => {
    process.exit(0);
  });
});
