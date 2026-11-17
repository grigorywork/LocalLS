'use strict';
// Transparent batching of many small files.
//
// A sender packs a set of small files into one ZIP and uploads it once. The
// LocalLS receiver recognises the marker suffix, extracts the archive next to
// itself and removes the archive, so the user only ever sees the files. A
// generic SFTP server keeps the ZIP, which is still an ordinary archive.
//
// The writer and reader are intentionally self-contained: no new dependency is
// shipped, and the reader never trusts sizes, names or compression from the
// other side. It refuses traversal, links, oversized entries and checks CRC.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const zlib = require('node:zlib');

const MARKER = '.localls.zip';
const NAME = /^LocalLS-\d{8}-\d{6}-[a-f0-9]{8}\.localls\.zip$/i;
const LIMITS = {
  files: 500,
  fileSize: 16 * 1024 * 1024,
  total: 192 * 1024 * 1024,
  entries: 2000,
  extracted: 256 * 1024 * 1024,
  name: 240,
};

const CRC = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let c = -1;
  for (let i = 0; i < buffer.length; i++) c = CRC[(c ^ buffer[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

// Already-compressed payloads would only burn CPU, so they are stored as-is.
function stored(name) {
  return /\.(?:jpe?g|png|gif|webp|heic|heif|avif|mp4|mov|m4v|mp3|m4a|aac|ogg|oga|opus|zip|7z|rar|gz|xz|bz2|apk|aab|docx|xlsx|pptx|odt|ods|epub|pdf)$/i.test(name);
}

function timestamp(value = new Date()) {
  const pad = n => String(n).padStart(2, '0');
  return value.getFullYear() + pad(value.getMonth() + 1) + pad(value.getDate()) + '-' + pad(value.getHours()) + pad(value.getMinutes()) + pad(value.getSeconds());
}

function archiveName(now = new Date()) {
  return 'LocalLS-' + timestamp(now) + '-' + crypto.randomBytes(4).toString('hex') + MARKER;
}

function isBatchName(value) {
  return typeof value === 'string' && NAME.test(path.basename(value));
}

function canBatch(entries) {
  if (!Array.isArray(entries) || entries.length < 2 || entries.length > LIMITS.files) return false;
  let total = 0;
  for (const entry of entries) {
    if (!entry || typeof entry.path !== 'string' || !Number.isSafeInteger(entry.size) || entry.size < 0 || entry.size > LIMITS.fileSize) return false;
    total += entry.size;
    if (total > LIMITS.total) return false;
  }
  return true;
}

function dosTime(date) {
  const year = Math.max(1980, date.getFullYear());
  return {
    time: (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1),
    date: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate(),
  };
}

function localHeader(name, crc, size, packed, method, stamp) {
  const nameBuffer = Buffer.from(name, 'utf8');
  const head = Buffer.alloc(30);
  head.writeUInt32LE(0x04034b50, 0);
  head.writeUInt16LE(20, 4);
  head.writeUInt16LE(0x0800, 6); // UTF-8 names
  head.writeUInt16LE(method, 8);
  head.writeUInt16LE(stamp.time, 10);
  head.writeUInt16LE(stamp.date, 12);
  head.writeUInt32LE(crc, 14);
  head.writeUInt32LE(packed, 18);
  head.writeUInt32LE(size, 22);
  head.writeUInt16LE(nameBuffer.length, 26);
  head.writeUInt16LE(0, 28);
  return Buffer.concat([head, nameBuffer]);
}

function centralHeader(name, crc, size, packed, method, stamp, offset) {
  const nameBuffer = Buffer.from(name, 'utf8');
  const head = Buffer.alloc(46);
  head.writeUInt32LE(0x02014b50, 0);
  head.writeUInt16LE(20, 4); // made by DOS: no Unix mode bits, so never a link
  head.writeUInt16LE(20, 6);
  head.writeUInt16LE(0x0800, 8);
  head.writeUInt16LE(method, 10);
  head.writeUInt16LE(stamp.time, 12);
  head.writeUInt16LE(stamp.date, 14);
  head.writeUInt32LE(crc, 16);
  head.writeUInt32LE(packed, 20);
  head.writeUInt32LE(size, 24);
  head.writeUInt16LE(nameBuffer.length, 28);
  head.writeUInt16LE(0, 30);
  head.writeUInt16LE(0, 32);
  head.writeUInt16LE(0, 34);
  head.writeUInt16LE(0, 36);
  head.writeUInt32LE(0, 38);
  head.writeUInt32LE(offset, 42);
  return Buffer.concat([head, nameBuffer]);
}

// entries: [{path, name}] already sanitised and unique. Returns the temp file.
async function createArchive(entries, {directory = os.tmpdir()} = {}) {
  if (!Array.isArray(entries) || entries.length < 1 || entries.length > LIMITS.files) throw new Error('BATCH_FILES');
  const file = path.join(directory, 'localls-batch-' + crypto.randomUUID() + '.zip');
  const handle = await fs.promises.open(file, 'wx', 0o600);
  const central = [];
  let offset = 0, total = 0;
  try {
    for (const entry of entries) {
      if (typeof entry.name !== 'string' || !entry.name || Buffer.byteLength(entry.name) > LIMITS.name || /[\\/\x00-\x1f]/.test(entry.name) || entry.name === '.' || entry.name === '..') throw new Error('BATCH_NAME');
      const info = await fs.promises.lstat(entry.path);
      if (info.isSymbolicLink() || !info.isFile()) throw new Error('BATCH_LINK');
      if (info.size > LIMITS.fileSize || !Number.isSafeInteger(info.size)) throw new Error('BATCH_LIMIT');
      total += info.size;
      if (total > LIMITS.total) throw new Error('BATCH_LIMIT');
      const data = await fs.promises.readFile(entry.path);
      if (data.length !== info.size) throw new Error('BATCH_CHANGED');
      const method = stored(entry.name) ? 0 : 8;
      const packed = method ? zlib.deflateRawSync(data, {level: 6}) : data;
      const stamp = dosTime(info.mtime);
      const crc = crc32(data);
      const header = localHeader(entry.name, crc, data.length, packed.length, method, stamp);
      await handle.write(header, 0, header.length, offset);
      await handle.write(packed, 0, packed.length, offset + header.length);
      central.push(centralHeader(entry.name, crc, data.length, packed.length, method, stamp, offset));
      offset += header.length + packed.length;
    }
    const directoryOffset = offset;
    for (const record of central) { await handle.write(record, 0, record.length, offset); offset += record.length; }
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(0, 4);
    end.writeUInt16LE(0, 6);
    end.writeUInt16LE(entries.length, 8);
    end.writeUInt16LE(entries.length, 10);
    end.writeUInt32LE(offset - directoryOffset, 12);
    end.writeUInt32LE(directoryOffset, 16);
    end.writeUInt16LE(0, 20);
    await handle.write(end, 0, end.length, offset);
    await handle.sync();
    return {file, bytes: offset + end.length, count: entries.length, total};
  } catch (error) {
    await handle.close().catch(() => {});
    await fs.promises.unlink(file).catch(() => {});
    throw error;
  } finally {
    await handle.close().catch(() => {});
  }
}

function readEOCD(buffer) {
  for (let at = buffer.length - 22; at >= 0; at--) {
    if (buffer.readUInt32LE(at) !== 0x06054b50) continue;
    return {
      count: buffer.readUInt16LE(at + 10),
      size: buffer.readUInt32LE(at + 12),
      offset: buffer.readUInt32LE(at + 16),
    };
  }
  throw new Error('BATCH_FORMAT');
}

function safeEntry(name) {
  if (typeof name !== 'string' || !name || name.length > LIMITS.name || /[\\\x00-\x1f]/.test(name)) throw new Error('BATCH_ENTRY');
  const parts = name.split('/');
  for (const part of parts) if (!part || part === '.' || part === '..' || part.length > 255) throw new Error('BATCH_ENTRY');
  return parts;
}

function inside(root, target) {
  const relative = path.relative(root, target);
  return relative === '' || relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
}

// extractArchive(archive, root): the archive must live under root, and every
// entry is staged in a hidden sibling directory before it is published.
async function extractArchive(archive, root) {
  const rootReal = fs.realpathSync(root);
  const info = fs.lstatSync(archive);
  if (!info.isFile() || info.isSymbolicLink()) throw new Error('BATCH_ARCHIVE');
  const archiveReal = fs.realpathSync(archive);
  if (!inside(rootReal, archiveReal)) throw new Error('BATCH_ARCHIVE');
  const handle = await fs.promises.open(archiveReal, 'r');
  let staging;
  try {
    const size = (await handle.stat()).size;
    if (!Number.isSafeInteger(size) || size < 22) throw new Error('BATCH_FORMAT');
    const readAt = async (buffer, offset, length, position) => (await handle.read(buffer, offset, length, position)).bytesRead;
    const tail = Buffer.alloc(Math.min(size, 66 * 1024));
    await readAt(tail, 0, tail.length, size - tail.length);
    const end = readEOCD(tail);
    if (!end.count || end.count > LIMITS.entries || end.size > 8 * 1024 * 1024 || end.offset + end.size > size) throw new Error('BATCH_FORMAT');
    const directory = Buffer.alloc(end.size);
    if (await readAt(directory, 0, end.size, end.offset) !== end.size) throw new Error('BATCH_FORMAT');
    const entries = [];
    let at = 0, total = 0;
    while (entries.length < end.count) {
      if (at + 46 > directory.length || directory.readUInt32LE(at) !== 0x02014b50) throw new Error('BATCH_FORMAT');
      const madeBy = directory.readUInt16LE(at + 4) >> 8;
      const method = directory.readUInt16LE(at + 10);
      const crc = directory.readUInt32LE(at + 16);
      const packed = directory.readUInt32LE(at + 20);
      const expanded = directory.readUInt32LE(at + 24);
      const nameLength = directory.readUInt16LE(at + 28);
      const extraLength = directory.readUInt16LE(at + 30);
      const commentLength = directory.readUInt16LE(at + 32);
      const external = directory.readUInt32LE(at + 38);
      const local = directory.readUInt32LE(at + 42);
      const next = at + 46 + nameLength + extraLength + commentLength;
      if (next > directory.length || nameLength < 1) throw new Error('BATCH_FORMAT');
      if (madeBy === 3 && (external >>> 16 & 0o170000) === 0o120000) throw new Error('BATCH_ENTRY');
      if (method !== 0 && method !== 8) throw new Error('BATCH_METHOD');
      const name = directory.toString('utf8', at + 46, at + 46 + nameLength);
      if (!name.endsWith('/')) {
        const parts = safeEntry(name);
        if (!Number.isSafeInteger(expanded) || expanded < 0 || expanded > LIMITS.total) throw new Error('BATCH_LIMIT');
        total += expanded;
        if (total > LIMITS.extracted || packed > size) throw new Error('BATCH_LIMIT');
        entries.push({name, parts, method, crc, packed, expanded, local});
      }
      at = next;
    }
    if (!entries.length) throw new Error('BATCH_EMPTY');
    staging = await fs.promises.mkdtemp(path.join(path.dirname(archiveReal), '.localls-extract-'));
    const staged = [];
    for (const entry of entries) {
      const head = Buffer.alloc(30);
      if (entry.local + 30 > size || await readAt(head, 0, 30, entry.local) !== 30 || head.readUInt32LE(0) !== 0x04034b50) throw new Error('BATCH_FORMAT');
      const nameLength = head.readUInt16LE(26), extraLength = head.readUInt16LE(28);
      const start = entry.local + 30 + nameLength + extraLength;
      if (start + entry.packed > size) throw new Error('BATCH_FORMAT');
      const raw = Buffer.alloc(entry.packed);
      if (entry.packed && await readAt(raw, 0, entry.packed, start) !== entry.packed) throw new Error('BATCH_FORMAT');
      let data;
      try { data = entry.method === 0 ? raw : zlib.inflateRawSync(raw, {maxOutputLength: Math.max(1, entry.expanded)}); }
      catch { throw new Error('BATCH_DATA'); }
      if (data.length !== entry.expanded || crc32(data) !== entry.crc) throw new Error('BATCH_DATA');
      const stagedFile = path.join(staging, crypto.randomUUID());
      await fs.promises.writeFile(stagedFile, data, {mode: 0o600});
      staged.push({parts: entry.parts, stagedFile, size: data.length, data});
    }
    await handle.close();
    const files = [];
    for (const entry of staged) {
      const folder = path.join(path.dirname(archiveReal), ...entry.parts.slice(0, -1));
      await fs.promises.mkdir(folder, {recursive: true});
      if (!inside(rootReal, fs.realpathSync(folder))) throw new Error('BATCH_ENTRY');
      let target = path.join(folder, entry.parts[entry.parts.length - 1]);
      for (let n = 1; fs.existsSync(target) && n < 10000; n++) {
        if (fs.lstatSync(target).isFile() && fs.statSync(target).size === entry.size && fs.readFileSync(target).equals(entry.data)) { target = null; break; }
        target = path.join(folder, duplicate(entry.parts[entry.parts.length - 1], n));
      }
      if (target) { await fs.promises.rename(entry.stagedFile, target); files.push({name: path.basename(target), relative: path.relative(rootReal, target).split(path.sep).join('/'), size: entry.size}); }
      else await fs.promises.unlink(entry.stagedFile).catch(() => {});
    }
    await fs.promises.rm(staging, {recursive: true, force: true}); staging = null;
    await fs.promises.unlink(archiveReal);
    return {count: files.length, files};
  } finally {
    await handle.close().catch(() => {});
    if (staging) await fs.promises.rm(staging, {recursive: true, force: true}).catch(() => {});
  }
}

function duplicate(base, n) { const ext = path.extname(base); return base.slice(0, base.length - ext.length) + ' (' + n + ')' + ext; }

// Leftovers from a crash or a cancelled queue item are removed on startup.
async function cleanupStale(directory = os.tmpdir(), maxAge = 6 * 60 * 60 * 1000) {
  let names = [];
  try { names = await fs.promises.readdir(directory); } catch { return 0; }
  let removed = 0;
  for (const name of names) {
    if (!/^localls-batch-[0-9a-f-]{36}\.zip$/.test(name)) continue;
    const file = path.join(directory, name);
    try { if (Date.now() - (await fs.promises.lstat(file)).mtimeMs > maxAge) { await fs.promises.unlink(file); removed++; } } catch {}
  }
  return removed;
}

module.exports = {MARKER, LIMITS, archiveName, isBatchName, canBatch, createArchive, extractArchive, cleanupStale};
