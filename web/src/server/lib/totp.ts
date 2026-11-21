import "server-only";
import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

// RFC 6238 time-based codes: 6 digits, 30-second steps, SHA-1, as every authenticator app expects.
const STEP_MS = 30_000;
const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function newTotpSecret(): string {
  const bytes = randomBytes(20);
  let bits = 0;
  let value = 0;
  let out = "";
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      out += ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

function base32Decode(secret: string): Buffer {
  const clean = secret.replace(/[\s=]/g, "").toUpperCase();
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const ch of clean) {
    const idx = ALPHABET.indexOf(ch);
    if (idx < 0) continue;
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

function codeAt(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const mac = createHmac("sha1", base32Decode(secret)).update(counter).digest();
  const offset = mac[mac.length - 1] & 15;
  const num = (mac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(num).padStart(6, "0");
}

/** The step the code matches, allowing one step of clock drift either way, or null. */
export function verifyTotp(secret: string, code: string, now = Date.now()): number | null {
  const typed = code.replace(/\s/g, "");
  if (!/^\d{6}$/.test(typed)) return null;
  const current = Math.floor(now / STEP_MS);
  for (const step of [current, current - 1, current + 1]) {
    const expected = codeAt(secret, step);
    if (timingSafeEqual(Buffer.from(expected), Buffer.from(typed))) return step;
  }
  return null;
}

export function totpUri(secret: string, account: string): string {
  const label = encodeURIComponent(`Trip Planner:${account}`);
  return `otpauth://totp/${label}?secret=${secret}&issuer=${encodeURIComponent("Trip Planner")}&algorithm=SHA1&digits=6&period=30`;
}

export const totpCodeForTest = codeAt;
