const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');

function get(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https') ? https : http;
    client.get(url, { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve(data);
        }
      });
    }).on('error', reject);
  });
}

function checkHead(url) {
  return new Promise((resolve) => {
    const client = url.startsWith('https') ? https : http;
    const req = client.request(url, { method: 'HEAD', headers: { 'User-Agent': 'Mozilla/5.0' } }, (res) => {
      resolve({ statusCode: res.statusCode, headers: res.headers });
    });
    req.on('error', (err) => resolve({ statusCode: 500, error: err.message }));
    req.setTimeout(8000, () => {
      req.destroy();
      resolve({ statusCode: 408 });
    });
    req.end();
  });
}

async function searchArchiveOrg(query) {
  const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl[]=identifier,title,downloads&sort[]=downloads+desc&rows=5&output=json`;
  const res = await get(url);
  return res?.response?.docs || [];
}

async function getItemFiles(identifier) {
  const url = `https://archive.org/metadata/${encodeURIComponent(identifier)}/files`;
  const res = await get(url);
  return res?.result || [];
}

async function testGame(fileUrl) {
  const head = await checkHead(fileUrl);
  return head.statusCode === 200 || head.statusCode === 302;
}

module.exports = { get, checkHead, searchArchiveOrg, getItemFiles, testGame };
