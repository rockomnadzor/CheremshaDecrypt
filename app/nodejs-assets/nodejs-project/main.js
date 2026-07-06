#!/usr/bin/env node
/**
 * Happ Deep-Link Decryptor — встроен в Android через nodejs-mobile.
 * Поднимает локальный HTTP-сервер, Kotlin делает обычные запросы к нему.
 */

'use strict';

const fs = require('fs');
const path = require('path');
const http = require('http');

let forge;
try {
  forge = require('node-forge');
} catch (e) {
  console.error('Error: node-forge is required.');
  process.exit(1);
}

function findDataDir() {
  const scriptDir = __dirname;
  const candidates = [
    path.join(scriptDir, 'public'),
    path.join(scriptDir, '..', 'public'),
  ];
  for (const d of candidates) {
    if (fs.existsSync(path.join(d, 'emu', 'liberror-code.so'))) return d;
  }
  return path.join(scriptDir, 'public');
}
const DATA_DIR = findDataDir();

function swapPairs(s) {
  const arr = [...s];
  for (let i = 0; i + 1 < arr.length; i += 2) [arr[i], arr[i + 1]] = [arr[i + 1], arr[i]];
  return arr.join('');
}
function b64DecodeUrlSafe(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  return Uint8Array.from(Buffer.from(s, 'base64'), (c) => c);
}
function uint8ToLatinStr(arr) {
  let s = '';
  for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
  return s;
}
function latinStrToUint8(str) {
  const out = new Uint8Array(str.length);
  for (let i = 0; i < str.length; i++) out[i] = str.charCodeAt(i) & 0xff;
  return out;
}

const PKCS1_KEYS_B64 = [
  "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",
];

const _forgeKeyCache = new Map();
function loadForgeKey(b64, header) {
  const cached = _forgeKeyCache.get(b64);
  if (cached) return cached;
  const lines = b64.replace(/\s/g, '').match(/.{1,64}/g).join('\n');
  const pem = `-----BEGIN ${header}-----\n${lines}\n-----END ${header}-----`;
  const key = forge.pki.privateKeyFromPem(pem);
  _forgeKeyCache.set(b64, key);
  return key;
}
function rsaDecrypt(privateKey, cipherBytes) { return privateKey.decrypt(uint8ToLatinStr(cipherBytes)); }

function m4831f(s) {
  const full = s.length - (s.length % 6);
  let out = '';
  for (let i = 0; i < full; i += 6) {
    const b = s.slice(i, i + 6);
    out += b[1] + b[3] + b[5] + b[0] + b[2] + b[4];
  }
  return out + s.slice(full);
}

const BASE = 0x100000, HOOKBASE = 0x40000000, HOOK_STOP = HOOKBASE + 0x800;
const STACK_BASE = 0x70000000, STACK_SIZE = 4 * 1024 * 1024;
const HEAP_BASE = 0x100000000, HEAP_SIZE = 64 * 1024 * 1024;
const MMAP_BASE = 0x200000000, MMAP_SIZE = 32 * 1024 * 1024;
const TLS_BASE = 0x300000000, TLS_SIZE = 64 * 1024;
const H_CLASS = 0x900001, H_MID = 0x900002, H_INARR = 0x900004, H_OUTARR = 0x900005;
function RX(i) { return i <= 28 ? 199 + i : (i === 29 ? 1 : 2); }
const R_LR = 2, R_SP = 4, R_PC = 260, R_TPIDR = 262;

function createDecryptor(opts) {
  const { MUnicorn, wrapperSrc, soBytes, keytable, verbose = 0 } = opts;
  return MUnicorn().then(M => {
    new Function('Module', wrapperSrc)(M);
    return { decrypt: (inBytes) => runOnce(M, wrapperSrc, soBytes, keytable, inBytes, verbose, opts) };
  });
}

function runOnce(M, wrapperSrc, soBytes, keytable, inBytes, verbose, opts) {
  const uc = new M.Unicorn(M.ARCH_ARM64, M.MODE_LITTLE_ENDIAN);
  const log = verbose ? (...a) => console.error(...a) : () => {};
  const regGet = (id) => { const b = uc.reg_read(id, 8); return b[0] + b[1] * 256 + b[2] * 65536 + b[3] * 16777216 + (b[4] + b[5] * 256 + b[6] * 65536 + b[7] * 16777216) * 4294967296; };
  const regSet = (id, v) => { v = Number(v); const lo = v >>> 0, hi = Math.floor(v / 4294967296) >>> 0; uc.reg_write(id, [lo & 255, (lo >> 8) & 255, (lo >> 16) & 255, (lo >> 24) & 255, hi & 255, (hi >> 8) & 255, (hi >> 16) & 255, (hi >> 24) & 255]); };
  const A = (i) => regGet(RX(i));
  const RET = (v) => regSet(RX(0), v);
  const gwrite = (addr, bytes) => uc.mem_write(addr, bytes);
  const gread = (addr, n) => uc.mem_read(addr, n);
  const gread32 = (addr) => { const b = gread(addr, 4); return (b[0] | b[1] << 8 | b[2] << 16 | b[3] << 24) >>> 0; };
  const gwrite64 = (addr, v) => { v = Number(v); const lo = v >>> 0, hi = Math.floor(v / 4294967296) >>> 0; gwrite(addr, [lo & 255, (lo >> 8) & 255, (lo >> 16) & 255, (lo >> 24) & 255, hi & 255, (hi >> 8) & 255, (hi >> 16) & 255, (hi >> 24) & 255]); };
  const greadCStr = (addr) => { let out = []; for (;;) { const chunk = gread(addr + out.length, 64); for (let i = 0; i < 64; i++) { if (chunk[i] === 0) return Uint8Array.from(out); out.push(chunk[i]); } } };
  const greadStr = (addr) => new TextDecoder().decode(greadCStr(addr));
  let heapPtr = HEAP_BASE;
  const allocSizes = new Map();
  const gmalloc = (n) => { n = Number(n) || 1; let p = Math.ceil(heapPtr / 16) * 16; allocSizes.set(p, n); heapPtr = p + n; if (heapPtr > HEAP_BASE + HEAP_SIZE) throw 'HEAP OOM'; return p; };
  const gsize = (p) => allocSizes.get(p) || 0;
  let mmapPtr = MMAP_BASE;
  const gmmap = (n) => { let p = Math.ceil(mmapPtr / 4096) * 4096; mmapPtr = p + Number(n); return p; };
  let inArrGuest = 0, inLen = inBytes.length;
  let outLen = 0, haveOut = false;
  const out = new Uint8Array(1 << 20);
  let lastNewStr = '';
  const mkJString = (s) => { const enc = new TextEncoder().encode(s); const p = gmalloc(enc.length + 8); gwrite64(p, enc.length); gwrite(p + 4, enc); gwrite(p + 4 + enc.length, [0]); return p; };
  const jsLen = (h) => gread32(h);
  function getHelp(markerIn) {
    const n = markerIn.length; let marker = ''; for (let i = 0; i < n; i++) marker += markerIn[n - 1 - i];
    let M0 = ''; for (let i = 0; i + 1 < n; i += 2) M0 += marker[i + 1]; if (n % 2 === 1) M0 += marker[n - 1];
    const key = keytable[M0]; if (!key) log('[getHelp] NO MATCH M0=' + M0); return key || '';
  }
  let redirect = -1;
  const handlers = [], hnames = [];
  const reg_hook = (name, fn) => { const i = handlers.length; handlers.push(fn); hnames.push(name); return HOOKBASE + i * 4; };
  const H = {};
  H.malloc = () => RET(gmalloc(A(0)));
  H.calloc = () => { const n = A(0) * A(1), p = gmalloc(n); gwrite(p, new Uint8Array(n)); RET(p); };
  H.realloc = () => { const o = A(0), n = A(1); if (!o) { RET(gmalloc(n)); return; } const os = gsize(o), p = gmalloc(n); gwrite(p, gread(o, Math.min(os, n))); RET(p); };
  H.free = () => RET(0);
  H.posix_memalign = () => { const pp = A(0), al = A(1), n = A(2); let p = gmalloc(n + al); p = Math.ceil(p / al) * al; gwrite64(pp, p); RET(0); };
  H.memcpy = () => { const d = A(0), s = A(1), n = A(2); if (n) gwrite(d, gread(s, n)); RET(d); };
  H.memmove = H.memcpy;
  H.memset = () => { const d = A(0), c = A(1), n = A(2); if (n) gwrite(d, new Uint8Array(n).fill(c & 255)); RET(d); };
  H.memcmp = () => { const a = gread(A(0), A(2)), b = gread(A(1), A(2)); for (let i = 0; i < a.length; i++) { if (a[i] !== b[i]) { RET(a[i] < b[i] ? -1 : 1); return; } } RET(0); };
  H.memchr = () => { const a = A(0), c = A(1) & 255, n = A(2); const m = gread(a, n); for (let i = 0; i < n; i++) if (m[i] === c) { RET(a + i); return; } RET(0); };
  H.strlen = () => RET(greadCStr(A(0)).length);
  H.strcmp = () => { const a = greadStr(A(0)), b = greadStr(A(1)); RET(a < b ? -1 : a > b ? 1 : 0); };
  H.strncmp = () => { const n = A(2); const a = greadStr(A(0)).slice(0, n), b = greadStr(A(1)).slice(0, n); RET(a < b ? -1 : a > b ? 1 : 0); };
  H.strcpy = () => { const d = A(0); const s = greadCStr(A(1)); gwrite(d, s); gwrite(d + s.length, [0]); RET(d); };
  H.strncpy = () => { const d = A(0), n = A(2); let s = greadCStr(A(1)); const buf = new Uint8Array(n); buf.set(s.slice(0, n)); gwrite(d, buf); RET(d); };
  H.strchr = () => { const a = A(0), c = A(1) & 255; const s = greadCStr(a); const i = s.indexOf(c); RET(i < 0 ? 0 : a + i); };
  H.strrchr = () => { const a = A(0), c = A(1) & 255; const s = greadCStr(a); const i = s.lastIndexOf(c); RET(i < 0 ? 0 : a + i); };
  H.strstr = () => { const a = A(0); const h = greadStr(a), nd = greadStr(A(1)); const i = h.indexOf(nd); RET(i < 0 ? 0 : a + i); };
  H.strdup = () => { const s = greadCStr(A(0)); const p = gmalloc(s.length + 1); gwrite(p, s); gwrite(p + s.length, [0]); RET(p); };
  H.strtol = () => RET(parseInt(greadStr(A(0)), A(2) || 10) | 0);
  H.strtoul = () => RET((parseInt(greadStr(A(0)), A(2) || 10) >>> 0));
  H.atoi = () => RET(parseInt(greadStr(A(0)), 10) | 0);
  H.strcspn = () => { const s = greadStr(A(0)), set = greadStr(A(1)); let i = 0; for (; i < s.length; i++) if (set.includes(s[i])) break; RET(i); };
  H.strspn = () => { const s = greadStr(A(0)), set = greadStr(A(1)); let i = 0; for (; i < s.length; i++) if (!set.includes(s[i])) break; RET(i); };
  H.strpbrk = () => { const a = A(0); const s = greadStr(a), set = greadStr(A(1)); for (let i = 0; i < s.length; i++) if (set.includes(s[i])) { RET(a + i); return; } RET(0); };
  let errnoLoc = 0;
  H.__errno = () => RET(errnoLoc);
  H.stub0 = () => RET(0);
  H.time = () => { const t = 1700000000; if (A(0)) gwrite64(A(0), t); RET(t); };
  H.clock_gettime = () => { const ts = A(1); if (ts) { gwrite64(ts, 1700000000); gwrite64(ts + 8, 0); } RET(0); };
  H.gettimeofday = () => { const tv = A(0); if (tv) { gwrite64(tv, 1700000000); gwrite64(tv + 8, 0); } RET(0); };
  let rng = 0x12345678 >>> 0;
  const rnd = () => { rng = (Math.imul(rng, 1103515245) + 12345) >>> 0; return rng; };
  H.rand = () => RET((rnd() >>> 16) & 0x7fff);
  H.srand = () => { rng = A(0) >>> 0; RET(0); };
  H.getentropy = () => { const n = A(1); const b = new Uint8Array(n); for (let i = 0; i < n; i++) b[i] = (rnd() >>> 16) & 255; gwrite(A(0), b); RET(0); };
  H.getpid = () => RET(1234);
  H.sysconf = () => RET(4096);
  H.mmap = () => RET(gmmap(A(1)));
  H.abort = () => { log('[guest abort]'); uc.emu_stop(); };
  H.__stack_chk_fail = () => { log('[stack_chk_fail]'); uc.emu_stop(); };
  H.__system_property_get = () => { if (A(1)) gwrite(A(1), [0]); RET(0); };
  H.getenv = () => RET(0);
  H.getauxval = () => { const t = A(0); RET(t === 16 ? 0x2 : t === 6 ? 4096 : 0); };
  H.pthread_self = () => RET(1);
  H.syscall = () => { const n = A(0); if (n === 278) { const buf = A(1), len = A(2); const b = new Uint8Array(len); for (let i = 0; i < len; i++) b[i] = (rnd() >>> 16) & 255; gwrite(buf, b); RET(len); return; } RET(0); };
  H.snprintf = () => { if (A(1)) gwrite(A(0), [0]); RET(0); };
  const tlsVals = new Map(); let tlsNext = 1;
  H.pthread_key_create = () => { const k = tlsNext++; if (A(0)) gwrite(A(0), [k & 255, (k >> 8) & 255, (k >> 16) & 255, (k >> 24) & 255]); RET(0); };
  H.pthread_key_delete = () => RET(0);
  H.pthread_setspecific = () => { tlsVals.set(A(0), A(1)); RET(0); };
  H.pthread_getspecific = () => RET(tlsVals.get(A(0)) || 0);
  H.pthread_once = () => { const ctrl = A(0), init = A(1); RET(0); if (gread32(ctrl) === 0) { gwrite(ctrl, [1, 0, 0, 0]); redirect = init; } };
  const J = {};
  J.FindClass = () => RET(H_CLASS); J.GetObjectClass = () => RET(H_CLASS);
  J.GetMethodID = () => RET(H_MID); J.GetStaticMethodID = () => RET(H_MID);
  J.NewStringUTF = () => { lastNewStr = greadStr(A(1)); RET(mkJString(lastNewStr)); };
  J.CallObjectMethodV = () => RET(H_INARR);
  J.CallStaticObjectMethodV = () => { const key = getHelp(lastNewStr); log('[getHelp] marker=' + lastNewStr + ' keylen=' + key.length); RET(mkJString(key)); };
  J.GetStringUTFChars = () => { if (A(2)) gwrite(A(2), [0, 0, 0, 0]); RET(A(1) + 4); };
  J.GetStringUTFLength = () => RET(jsLen(A(1)));
  J.ReleaseStringUTFChars = () => RET(0);
  J.GetArrayLength = () => RET(A(1) === H_INARR ? inLen : outLen);
  J.GetByteArrayElements = () => { if (A(2)) gwrite(A(2), [0, 0, 0, 0]); RET(inArrGuest); };
  J.ReleaseByteArrayElements = () => RET(0);
  J.DeleteLocalRef = () => RET(0);
  J.NewByteArray = () => { outLen = A(1); RET(H_OUTARR); };
  J.SetByteArrayRegion = () => { const start = A(2), len = A(3), buf = A(4); if (start + len <= out.length) { out.set(gread(buf, len), start); haveOut = true; } RET(0); };
  J.ExceptionCheck = () => RET(0); J.ExceptionOccurred = () => RET(0); J.ExceptionClear = () => RET(0);
  J.ThrowNew = () => { log('[ThrowNew] ' + (A(2) ? greadStr(A(2)) : '')); RET(0); };
  function resolveImport(name) { if (H[name]) return reg_hook(name, H[name]); return reg_hook(name, H.stub0); }
  const dv = new DataView(soBytes.buffer, soBytes.byteOffset, soBytes.byteLength);
  const u32 = (o) => dv.getUint32(o, true), u16 = (o) => dv.getUint16(o, true), u64 = (o) => dv.getUint32(o, true) + dv.getUint32(o + 4, true) * 4294967296;
  const e_phoff = u64(0x20), e_phnum = u16(0x38);
  let maxv = 0; const loads = [];
  for (let i = 0; i < e_phnum; i++) { const p = e_phoff + i * 56; const type = u32(p); if (type === 1) { const off = u64(p + 8), va = u64(p + 16), fsz = u64(p + 32), msz = u64(p + 40); loads.push({ off, va, fsz }); if (va + msz > maxv) maxv = va + msz; } }
  const span = Math.ceil((maxv + 0xffff) / 0x10000) * 0x10000;
  const sobk = new Uint8Array(span);
  for (const L of loads) sobk.set(soBytes.subarray(L.off, L.off + L.fsz), L.va);
  const sdv = new DataView(sobk.buffer);
  const s32 = (o) => sdv.getUint32(o, true), s64 = (o) => sdv.getUint32(o, true) + sdv.getUint32(o + 4, true) * 4294967296;
  const sset64 = (o, v) => { v = Number(v); sdv.setUint32(o, v >>> 0, true); sdv.setUint32(o + 4, Math.floor(v / 4294967296) >>> 0, true); };
  let dynVa = 0;
  for (let i = 0; i < e_phnum; i++) { const p = e_phoff + i * 56; if (u32(p) === 2) dynVa = u64(p + 16); }
  let rela = 0, relasz = 0, jmprel = 0, pltsz = 0, symtab = 0, strtab = 0, syment = 24, initarr = 0, initarrsz = 0;
  for (let d = dynVa; ; d += 16) { const tag = s64(d), val = s64(d + 8); if (tag === 0) break; if (tag === 7) rela = val; else if (tag === 8) relasz = val; else if (tag === 23) jmprel = val; else if (tag === 2) pltsz = val; else if (tag === 6) symtab = val; else if (tag === 5) strtab = val; else if (tag === 11) syment = val; else if (tag === 25) initarr = val; else if (tag === 27) initarrsz = val; }
  const symName = (idx) => { const nameOff = s32(symtab + idx * syment); let s = ''; for (let i = strtab + nameOff; ; i++) { const c = sobk[i]; if (!c) break; s += String.fromCharCode(c); } return s; };
  const symShndx = (idx) => sdv.getUint16(symtab + idx * syment + 6, true);
  const symValue = (idx) => s64(symtab + idx * syment + 8);
  function applyRelocs(r, sz) { for (let o = 0; o < sz; o += 24) { const off = s64(r + o), info_lo = s32(r + o + 8), info_hi = s32(r + o + 12), add = s64(r + o + 16); const type = info_lo, symi = info_hi; if (type === 1027) { sset64(off, BASE + add); } else if (type === 1026 || type === 1025 || type === 257) { if (symShndx(symi)) sset64(off, BASE + symValue(symi) + (type === 257 ? add : 0)); else sset64(off, resolveImport(symName(symi))); } } }
  applyRelocs(rela, relasz); applyRelocs(jmprel, pltsz);
  errnoLoc = gmalloc(8);
  inArrGuest = gmalloc(inLen);
  const table = gmalloc(0x800);
  const tbuf = new Uint8Array(0x800);
  const tdv = new DataView(tbuf.buffer);
  const JSET = (off, name, fn) => { const a = reg_hook(name, fn); tdv.setUint32(off, a >>> 0, true); tdv.setUint32(off + 4, Math.floor(a / 4294967296) >>> 0, true); };
  JSET(0x30, 'FindClass', J.FindClass); JSET(0x88, 'ExceptionClear', J.ExceptionClear);
  JSET(0xb8, 'DeleteLocalRef', J.DeleteLocalRef); JSET(0xf8, 'GetObjectClass', J.GetObjectClass);
  JSET(0x108, 'GetMethodID', J.GetMethodID); JSET(0x118, 'CallObjectMethodV', J.CallObjectMethodV);
  JSET(0x388, 'GetStaticMethodID', J.GetStaticMethodID); JSET(0x398, 'CallStaticObjectMethodV', J.CallStaticObjectMethodV);
  JSET(0x538, 'NewStringUTF', J.NewStringUTF); JSET(0x540, 'GetStringUTFLength', J.GetStringUTFLength);
  JSET(0x548, 'GetStringUTFChars', J.GetStringUTFChars); JSET(0x550, 'ReleaseStringUTFChars', J.ReleaseStringUTFChars);
  JSET(0x558, 'GetArrayLength', J.GetArrayLength); JSET(0x580, 'NewByteArray', J.NewByteArray);
  JSET(0x5c0, 'GetByteArrayElements', J.GetByteArrayElements); JSET(0x600, 'ReleaseByteArrayElements', J.ReleaseByteArrayElements);
  JSET(0x680, 'SetByteArrayRegion', J.SetByteArrayRegion); JSET(0x720, 'ExceptionCheck', J.ExceptionCheck);
  JSET(0x78, 'ExceptionOccurred', J.ExceptionOccurred); JSET(0x35c, 'ThrowNew', J.ThrowNew);
  const envp = gmalloc(8);
  uc.mem_map(BASE, span, M.PROT_ALL);
  uc.mem_map(STACK_BASE, STACK_SIZE, M.PROT_ALL);
  uc.mem_map(HEAP_BASE, HEAP_SIZE, M.PROT_ALL);
  uc.mem_map(MMAP_BASE, MMAP_SIZE, M.PROT_ALL);
  uc.mem_map(TLS_BASE, TLS_SIZE, M.PROT_ALL);
  const hookpage = new Uint8Array(0x1000);
  for (let i = 0; i < 0x1000; i += 4) { hookpage[i] = 0xc0; hookpage[i + 1] = 0x03; hookpage[i + 2] = 0x5f; hookpage[i + 3] = 0xd6; }
  uc.mem_map(HOOKBASE, 0x1000, M.PROT_ALL);
  uc.mem_write(HOOKBASE, hookpage);
  uc.mem_write(BASE, sobk);
  uc.mem_write(table, tbuf);
  gwrite64(envp, table);
  gwrite64(errnoLoc, 0);
  gwrite(inArrGuest, inBytes);
  regSet(R_TPIDR, TLS_BASE + 0x1000);
  gwrite64(TLS_BASE + 0x28, 0);
  regSet(R_SP, STACK_BASE + STACK_SIZE - 0x100);
  uc.hook_add(M.HOOK_CODE, (handle, address, size, ud) => { const addr = Number(address); if (addr >= HOOKBASE && addr < HOOKBASE + 0x800) { const idx = (addr - HOOKBASE) / 4; if (idx < handlers.length) { redirect = -1; handlers[idx](); const tgt = redirect >= 0 ? redirect : regGet(R_LR); redirect = -1; regSet(R_PC, tgt); } } }, null, HOOKBASE, HOOKBASE + 0x800);
  const sgread32 = (a) => { const b = gread(a, 4); return (b[0] | b[1] << 8 | b[2] << 16 | b[3] << 24) | 0; };
  const gread64v = (a) => { const b = gread(a, 8); return b[0] + b[1] * 256 + b[2] * 65536 + b[3] * 16777216 + (b[4] + b[5] * 256 + b[6] * 65536 + b[7] * 16777216) * 4294967296; };
  const inHeapRange = (p) => p >= HEAP_BASE && p < HEAP_BASE + HEAP_SIZE;
  function bnRead(p) { if (!inHeapRange(p) || (p & 7)) return null; const dptr = gread64v(p), top = sgread32(p + 8), dmax = sgread32(p + 12), neg = sgread32(p + 16); if (top < 0 || top > 256 || dmax < top || neg < 0 || neg > 1) return null; if (top === 0) return { val: 0n, top: 0, dmax, neg }; if (!inHeapRange(dptr)) return null; const limbs = gread(dptr, top * 8); let val = 0n; for (let i = top - 1; i >= 0; i--) { let limb = 0n; for (let b = 7; b >= 0; b--) limb = (limb << 8n) | BigInt(limbs[i * 8 + b]); val = (val << 64n) | limb; } return { val, top, dmax, neg }; }
  function bnWrite(p, val) { let v = val < 0n ? -val : val; const bytes = []; while (v > 0n) { bytes.push(Number(v & 0xffn)); v >>= 8n; } while (bytes.length % 8) bytes.push(0); if (bytes.length === 0) for (let i = 0; i < 8; i++) bytes.push(0); const cap = bytes.length / 8; let top = cap; while (top > 0) { let z = true; for (let b = 0; b < 8; b++) if (bytes[(top - 1) * 8 + b]) { z = false; break; } if (z) top--; else break; } const d = gmalloc(cap * 8); gwrite(d, Uint8Array.from(bytes)); gwrite64(p, d); gwrite(p + 8, [top & 255, (top >> 8) & 255, (top >> 16) & 255, (top >> 24) & 255]); gwrite(p + 12, [cap & 255, (cap >> 8) & 255, (cap >> 16) & 255, (cap >> 24) & 255]); gwrite(p + 16, [0, 0, 0, 0]); }
  function bnModPow(b, e, m) { if (m === 1n) return 0n; b %= m; if (b < 0n) b += m; let r = 1n; while (e > 0n) { if (e & 1n) r = (r * b) % m; e >>= 1n; b = (b * b) % m; } return r; }
  const MODEXP_OFF = 0x1c5ef8, MODEXP_PROLOGUE = [0xa9ba7bfd, 0xa9016ffc, 0xa90267fa];
  let modexpSkips = 0;
  let MA = opts.modexpAddr ? BASE + opts.modexpAddr : 0;
  if (!MA && !opts.noFastPath) { const b = gread(BASE + MODEXP_OFF, 12); let ok = true; for (let i = 0; i < 3; i++) { const w = (b[i * 4] | b[i * 4 + 1] << 8 | b[i * 4 + 2] << 16 | b[i * 4 + 3] << 24) >>> 0; if (w !== MODEXP_PROLOGUE[i]) ok = false; } if (ok) MA = BASE + MODEXP_OFF; else log('[fastpath] modexp prologue mismatch'); }
  if (MA) { uc.hook_add(M.HOOK_CODE, (h, address) => { if (Number(address) !== MA) return; const rr = A(0), a = A(1), p = A(2), m = A(3); const A_ = bnRead(a), P_ = bnRead(p), M_ = bnRead(m), R_ = bnRead(rr); if (!A_ || !P_ || !M_ || !R_) return; if (M_.top < 16 || M_.neg || A_.neg || P_.neg) return; if (P_.top < 1 || P_.top > M_.top + 1 || A_.top > M_.top + 1) return; if ((M_.val & 1n) === 0n) return; bnWrite(rr, bnModPow(A_.val, P_.val, M_.val)); RET(1); regSet(R_PC, regGet(R_LR)); modexpSkips++; }, null, MA, MA + 4); }
  opts._skips = () => modexpSkips;
  if (initarr) for (let o = 0; o < initarrsz; o += 8) { const fn = s64(initarr + o); if (!fn) continue; regSet(R_LR, HOOK_STOP); try { uc.emu_start(fn, HOOK_STOP, 0, 0); } catch (e) { log('[init err] ' + e); } }
  const ENTRY = 'Java_su_happ_proxyutility_util_ErrorCodeJNIWrapper_jniGetErrorMessageFromString2';
  let entry = 0;
  for (let so = symtab; so < strtab; so += syment) { const shndx = sdv.getUint16(so + 6, true); const nameOff = s32(so); if (shndx && nameOff) { let s = ''; for (let i = strtab + nameOff; ; i++) { const c = sobk[i]; if (!c) break; s += String.fromCharCode(c); } if (s === ENTRY) { entry = BASE + s64(so + 8); break; } } }
  if (!entry) throw new Error('entry symbol not found');
  log('[entry] file 0x' + (entry - BASE).toString(16) + ' inLen=' + inLen);
  regSet(RX(0), envp); regSet(RX(1), 1); regSet(RX(2), H_INARR);
  regSet(R_SP, STACK_BASE + STACK_SIZE - 0x100);
  regSet(R_LR, HOOK_STOP);
  try { uc.emu_start(entry, HOOK_STOP, 0, 0); } catch (e) { log('[emu_start error] ' + e); }
  log('[done] haveOut=' + haveOut + ' outLen=' + outLen);
  uc.close();
  return out.slice(0, outLen);
}

function evalCjs(src, __dirname_val, __filename_val) {
  const m = { exports: {} };
  const fn = new Function('module', 'exports', 'require', '__dirname', '__filename', src);
  fn(m, m.exports, require, __dirname_val || '', __filename_val || '');
  return m.exports.default || m.exports;
}

let _nativePromise = null;
async function buildNativeDecryptor() {
  const base = DATA_DIR;
  const unicornPath = path.join(base, 'emu', 'unicorn_aarch64.js');
  const wrapperPath = path.join(base, 'emu', 'unicorn-wrapper.js');
  const soPath = path.join(base, 'emu', 'liberror-code.so');
  const keytablePath = path.join(base, 'data', 'keytable.json');
  for (const p of [unicornPath, wrapperPath, soPath, keytablePath]) {
    if (!fs.existsSync(p)) throw new Error('Missing required file: ' + p);
  }
  const unicornSrc = fs.readFileSync(unicornPath, 'utf8');
  const wrapperSrc = fs.readFileSync(wrapperPath, 'utf8');
  const soBuf = fs.readFileSync(soPath);
  const keytable = JSON.parse(fs.readFileSync(keytablePath, 'utf8'));
  const unicornDir = path.join(base, 'emu');
  const MUnicorn = evalCjs(unicornSrc, unicornDir, path.join(unicornDir, 'unicorn_aarch64.js'));
  return createDecryptor({ MUnicorn, wrapperSrc, soBytes: new Uint8Array(soBuf), keytable, verbose: 1 });
}
function getNativeDecryptor() { if (!_nativePromise) _nativePromise = buildNativeDecryptor(); return _nativePromise; }

async function decryptCrypt5(payload) {
  const nativeIn = m4831f(payload);
  const decryptor = await getNativeDecryptor();
  const outBytes = decryptor.decrypt(new TextEncoder().encode(nativeIn));
  if (!outBytes || outBytes.length === 0) throw new Error('crypt5 decryption failed (unknown marker/key or malformed link)');
  const obfuscated = new TextDecoder().decode(outBytes);
  return new TextDecoder().decode(b64DecodeUrlSafe(swapPairs(obfuscated)));
}

async function decryptCrypt1to4(ordinal, payload) {
  const privateKey = loadForgeKey(PKCS1_KEYS_B64[ordinal], 'RSA PRIVATE KEY');
  const keySize = Math.ceil(privateKey.n.bitLength() / 8);
  const cipherBytes = b64DecodeUrlSafe(payload);
  let plaintext = '';
  for (let i = 0; i < cipherBytes.length; i += keySize) plaintext += rsaDecrypt(privateKey, cipherBytes.slice(i, i + keySize));
  return new TextDecoder().decode(latinStrToUint8(plaintext));
}

async function decryptLink(link) {
  const p = link.startsWith('happ://') ? link.slice(7) : link;
  if (p.startsWith('crypt5/')) return decryptCrypt5(p.slice(7));
  if (p.startsWith('crypt4/')) return decryptCrypt1to4(3, p.slice(7));
  if (p.startsWith('crypt3/')) return decryptCrypt1to4(2, p.slice(7));
  if (p.startsWith('crypt2/')) return decryptCrypt1to4(1, p.slice(7));
  if (p.startsWith('crypt/')) return decryptCrypt1to4(0, p.slice(6));
  throw new Error('Unknown link format: ' + link);
}

// ============================================================================
// HTTP-СЕРВЕР — вместо CLI, Kotlin обращается сюда
// ============================================================================
const PORT = 51720;

const server = http.createServer((req, res) => {
  if (req.method !== 'POST' || req.url !== '/decrypt') {
    res.writeHead(404);
    res.end('not found');
    return;
  }
  let body = '';
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', async () => {
    try {
      const { link } = JSON.parse(body);
      const url = await decryptLink(link);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: true, url }));
    } catch (err) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: false, error: err.message }));
    }
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('crypt5 node server listening on ' + PORT);
});
