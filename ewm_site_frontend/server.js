const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = process.env.PORT || 4174;
const MAIN_API = process.env.MAIN_API || 'http://localhost:8080';
const STATS_API = process.env.STATS_API || 'http://localhost:9090';
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

async function readRequestBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  return Buffer.concat(chunks);
}

async function proxyRequest(req, res, targetBase, prefix) {
  try {
    const incomingUrl = new URL(req.url, `http://${req.headers.host}`);
    const targetUrl = new URL(targetBase + incomingUrl.pathname.replace(prefix, '') + incomingUrl.search);
    const body = ['GET', 'HEAD'].includes(req.method) ? undefined : await readRequestBody(req);

    const headers = { ...req.headers };
    delete headers.host;
    delete headers.connection;
    delete headers['content-length'];

    const upstream = await fetch(targetUrl, {
      method: req.method,
      headers,
      body
    });

    const responseBody = Buffer.from(await upstream.arrayBuffer());
    const responseHeaders = {};
    upstream.headers.forEach((value, key) => {
      if (!['content-encoding', 'transfer-encoding', 'connection'].includes(key.toLowerCase())) {
        responseHeaders[key] = value;
      }
    });

    res.writeHead(upstream.status, responseHeaders);
    res.end(responseBody);
  } catch (error) {
    sendJson(res, 502, {
      message: 'Не удалось подключиться к backend-сервису.',
      details: error.message,
      target: targetBase
    });
  }
}

function serveStatic(req, res) {
  let requestPath = req.url.split('?')[0];
  if (requestPath === '/') requestPath = '/index.html';

  const filePath = path.normalize(path.join(PUBLIC_DIR, requestPath));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    sendJson(res, 403, { message: 'Доступ запрещён.' });
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      sendJson(res, 404, { message: 'Файл не найден.' });
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/main')) return proxyRequest(req, res, MAIN_API, '/api/main');
  if (req.url.startsWith('/api/stats')) return proxyRequest(req, res, STATS_API, '/api/stats');
  if (req.url === '/config.json') {
    return sendJson(res, 200, { mainApi: MAIN_API, statsApi: STATS_API, port: PORT });
  }
  serveStatic(req, res);
});

server.listen(PORT, () => {
  console.log(`Frontend запущен: http://localhost:${PORT}`);
  console.log(`Прокси на основной сервис: ${MAIN_API}`);
  console.log(`Прокси на сервис статистики: ${STATS_API}`);
});
