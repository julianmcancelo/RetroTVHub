const https = require('https');
const http = require('http');

function getJson(url) {
  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'RetroTVHub-Crawler/1.0' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (e) { resolve(null); }
      });
    }).on('error', () => resolve(null));
  });
}

function checkHead(url) {
  return new Promise((resolve) => {
    try {
      const u = new URL(url);
      const client = u.protocol === 'https:' ? https : http;
      const req = client.request(url, { method: 'HEAD', headers: { 'User-Agent': 'Mozilla/5.0' } }, (res) => {
        resolve({ statusCode: res.statusCode, location: res.headers.location, contentLength: res.headers['content-length'] });
      });
      req.on('error', () => resolve({ statusCode: 500 }));
      req.setTimeout(6000, () => { req.destroy(); resolve({ statusCode: 408 }); });
      req.end();
    } catch (e) { resolve({ statusCode: 400 }); }
  });
}

async function verifyUrl(url) {
  const res = await checkHead(url);
  if (res.statusCode === 200) return { ok: true, size: res.contentLength };
  if (res.statusCode === 302 || res.statusCode === 301) {
    if (res.location) {
      const res2 = await checkHead(res.location);
      if (res2.statusCode === 200 || res2.statusCode === 302) return { ok: true, size: res2.contentLength || res.contentLength };
    }
    return { ok: true, size: res.contentLength };
  }
  return { ok: false, status: res.statusCode };
}

async function searchAndFind(query, extRegex, minBytes, maxBytes) {
  const searchUrl = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl[]=identifier,title,downloads&sort[]=downloads+desc&rows=15&output=json`;
  const searchRes = await getJson(searchUrl);
  if (!searchRes || !searchRes.response || !searchRes.response.docs) return null;

  for (const doc of searchRes.response.docs) {
    const metaUrl = `https://archive.org/metadata/${encodeURIComponent(doc.identifier)}/files`;
    const meta = await getJson(metaUrl);
    if (!meta || !meta.result) continue;

    for (const f of meta.result) {
      const sz = parseInt(f.size || 0, 10);
      if (extRegex.test(f.name) && sz >= minBytes && sz <= maxBytes) {
        const downloadUrl = `https://archive.org/download/${encodeURIComponent(doc.identifier)}/${encodeURIComponent(f.name)}`;
        const check = await verifyUrl(downloadUrl);
        if (check.ok) {
          return {
            title: doc.title,
            identifier: doc.identifier,
            fileName: f.name,
            url: downloadUrl,
            size: sz
          };
        }
      }
    }
  }
  return null;
}

async function run() {
  const targets = [
    { key: 'Contra (NES)', q: 'Contra AND (nes OR "Nintendo Entertainment System")', ext: /\.(nes|zip)$/i, min: 50000, max: 500000 },
    { key: 'Castlevania (NES)', q: 'Castlevania AND (nes OR "Nintendo Entertainment System")', ext: /\.(nes|zip)$/i, min: 50000, max: 500000 },
    { key: 'Mega Man 2 (NES)', q: '"Mega Man 2" OR "Megaman 2" AND (nes OR "Nintendo Entertainment System")', ext: /\.(nes|zip)$/i, min: 100000, max: 600000 },
    { key: 'Super Metroid (SNES)', q: '"Super Metroid" AND (snes OR "Super Nintendo")', ext: /\.(sfc|smc|zip)$/i, min: 1000000, max: 4000000 },
    { key: 'Street Fighter II Turbo (SNES)', q: '"Street Fighter II Turbo" AND (snes OR "Super Nintendo")', ext: /\.(sfc|smc|zip)$/i, min: 1500000, max: 5000000 },
    { key: 'Sonic the Hedgehog 2 (Genesis)', q: '"Sonic the Hedgehog 2" AND (genesis OR "mega drive")', ext: /\.(md|bin|gen|zip)$/i, min: 500000, max: 3000000 },
    { key: 'Super Mario 64 (N64)', q: '"Super Mario 64" AND (n64 OR "Nintendo 64" OR z64)', ext: /\.(z64|n64|v64|zip)$/i, min: 6000000, max: 12000000 },
    { key: 'Zelda Ocarina of Time (N64)', q: '"Ocarina of Time" AND (n64 OR "Nintendo 64" OR z64)', ext: /\.(z64|n64|v64|zip)$/i, min: 20000000, max: 40000000 },
    { key: 'Crash Bandicoot (PS1)', q: '"Crash Bandicoot" AND (chd OR "playstation" OR psx)', ext: /\.(chd|pbp|bin|iso|zip)$/i, min: 200000000, max: 600000000 }
  ];

  const results = {};
  for (const t of targets) {
    process.stdout.write(`Scanning for ${t.key}... `);
    const found = await searchAndFind(t.q, t.ext, t.min, t.max);
    if (found) {
      console.log(`FOUND! -> ${found.fileName} (${(found.size / 1024 / 1024).toFixed(2)} MB) in [${found.identifier}]`);
      results[t.key] = found;
    } else {
      console.log('NOT FOUND.');
    }
  }

  const existing = JSON.parse(require('fs').readFileSync('./scripts/verified_roms.json', 'utf8'));
  const merged = { ...existing, ...results };
  require('fs').writeFileSync('./scripts/verified_roms.json', JSON.stringify(merged, null, 2));
  console.log('Updated verified_roms.json successfully!');
}

run();
