const https = require('https');
const http = require('http');

function getJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'RetroTVHub-Crawler/1.0' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve(null);
        }
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
      req.setTimeout(6000, () => {
        req.destroy();
        resolve({ statusCode: 408 });
      });
      req.end();
    } catch (e) {
      resolve({ statusCode: 400 });
    }
  });
}

async function verifyUrl(url) {
  const res = await checkHead(url);
  if (res.statusCode === 200) return { ok: true, size: res.contentLength };
  if (res.statusCode === 302 || res.statusCode === 301) {
    if (res.location) {
      const res2 = await checkHead(res.location);
      if (res2.statusCode === 200 || res2.statusCode === 302) {
        return { ok: true, size: res2.contentLength || res.contentLength };
      }
    }
    return { ok: true, size: res.contentLength };
  }
  return { ok: false, status: res.statusCode };
}

async function searchAndFind(query, extRegex) {
  const searchUrl = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl[]=identifier,title,downloads&sort[]=downloads+desc&rows=8&output=json`;
  const searchRes = await getJson(searchUrl);
  if (!searchRes || !searchRes.response || !searchRes.response.docs) return null;

  for (const doc of searchRes.response.docs) {
    const metaUrl = `https://archive.org/metadata/${encodeURIComponent(doc.identifier)}/files`;
    const meta = await getJson(metaUrl);
    if (!meta || !meta.result) continue;

    for (const f of meta.result) {
      if (extRegex.test(f.name)) {
        const downloadUrl = `https://archive.org/download/${encodeURIComponent(doc.identifier)}/${encodeURIComponent(f.name)}`;
        const check = await verifyUrl(downloadUrl);
        if (check.ok) {
          return {
            title: doc.title,
            identifier: doc.identifier,
            fileName: f.name,
            url: downloadUrl,
            size: parseInt(f.size || check.size || 0, 10)
          };
        }
      }
    }
  }
  return null;
}

async function run() {
  const targets = [
    // NES
    { name: 'Super Mario Bros 3 (NES)', q: 'title:"Super Mario Bros 3" AND mediatype:software', ext: /\.(nes|zip)$/i },
    { name: 'Contra (NES)', q: 'title:"Contra" AND mediatype:software', ext: /\.(nes|zip)$/i },
    { name: 'Castlevania (NES)', q: 'title:"Castlevania" AND mediatype:software', ext: /\.(nes|zip)$/i },
    { name: 'Mega Man 2 (NES)', q: 'title:"Mega Man 2" AND mediatype:software', ext: /\.(nes|zip)$/i },

    // SNES
    { name: 'Super Mario World (SNES)', q: 'identifier:"super-mario-world-usa_202406"', ext: /\.(sfc|smc|zip)$/i },
    { name: 'Zelda A Link to the Past (SNES)', q: 'identifier:"legend_of_zelda_a_link_to_the_past"', ext: /\.(sfc|smc|zip|snes)$/i },
    { name: 'Donkey Kong Country (SNES)', q: 'title:"Donkey Kong Country" AND mediatype:software', ext: /\.(sfc|smc|zip)$/i },
    { name: 'Super Metroid (SNES)', q: 'title:"Super Metroid" AND mediatype:software', ext: /\.(sfc|smc|zip)$/i },
    { name: 'Street Fighter II (SNES)', q: 'title:"Street Fighter II Turbo" AND mediatype:software', ext: /\.(sfc|smc|zip)$/i },

    // Genesis
    { name: 'Sonic 2 (Genesis)', q: 'title:"Sonic the Hedgehog 2" AND mediatype:software', ext: /\.(md|bin|gen|zip)$/i },
    { name: 'Streets of Rage 2 (Genesis)', q: 'title:"Streets of Rage 2" AND mediatype:software', ext: /\.(md|bin|gen|zip)$/i },
    { name: 'Gunstar Heroes (Genesis)', q: 'title:"Gunstar Heroes" AND mediatype:software', ext: /\.(md|bin|gen|zip)$/i },

    // GBA
    { name: 'Pokemon Emerald (GBA)', q: 'identifier:"pokemon-emerald-version-u_202303"', ext: /\.(gba|zip)$/i },
    { name: 'Zelda Minish Cap (GBA)', q: 'title:"Minish Cap" AND mediatype:software', ext: /\.(gba|zip)$/i },
    { name: 'Metroid Fusion (GBA)', q: 'title:"Metroid Fusion" AND mediatype:software', ext: /\.(gba|zip)$/i },

    // N64
    { name: 'Super Mario 64 (N64)', q: 'title:"Super Mario 64" AND mediatype:software', ext: /\.(z64|n64|v64|zip)$/i },
    { name: 'Mario Kart 64 (N64)', q: 'title:"Mario Kart 64" AND mediatype:software', ext: /\.(z64|n64|v64|zip)$/i },
    { name: 'Zelda Ocarina of Time (N64)', q: 'title:"Ocarina of Time" AND mediatype:software', ext: /\.(z64|n64|v64|zip)$/i },

    // PS1
    { name: 'Crash Bandicoot (PS1)', q: 'title:"Crash Bandicoot" AND (chd OR bin OR iso OR cue OR pbp)', ext: /\.(chd|pbp|bin|iso|zip)$/i },
    { name: 'Crash Team Racing (PS1)', q: 'title:"Crash Team Racing" AND (chd OR bin OR iso OR cue OR pbp)', ext: /\.(chd|pbp|bin|iso|zip)$/i },
    { name: 'Castlevania SotN (PS1)', q: 'title:"Symphony of the Night" AND (chd OR bin OR iso OR cue OR pbp)', ext: /\.(chd|pbp|bin|iso|zip)$/i }
  ];

  console.log(`Starting scan of ${targets.length} games...`);
  const results = {};

  for (const t of targets) {
    process.stdout.write(`Scanning for ${t.name}... `);
    const found = await searchAndFind(t.q, t.ext);
    if (found) {
      console.log(`FOUND! -> ${found.fileName} (${(found.size / 1024 / 1024).toFixed(2)} MB)`);
      results[t.name] = found;
    } else {
      console.log(`NOT FOUND.`);
    }
  }

  require('fs').writeFileSync('./scripts/verified_roms.json', JSON.stringify(results, null, 2));
  console.log('\nWrote verified_roms.json successfully!');
}

run();
