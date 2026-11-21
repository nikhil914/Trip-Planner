// Field-level encryption for the columns that hold personal data.
//
// DATA_ENCRYPTION_KEY is 32 random bytes in base64. Two keys are derived from it with HKDF:
// one for AES-256-GCM with a random IV (every other field), and one for a deterministic AES-256-GCM
// whose IV is an HMAC of the value (email only, so sign-in can still look an account up by email).
//
// Text is stored as "enc:v1:<base64url(iv | ciphertext | tag)>". Files start with the 4 bytes "TPE1".
// A value without the marker is read as plain text, so rows written before encryption still load,
// and the startup pass in encrypt-existing.ts rewrites them.
//
// No "server-only" import here: drizzle-kit loads the schema that uses these types.
import { createCipheriv, createDecipheriv, createHmac, hkdfSync, randomBytes } from "node:crypto";
import { customType } from "drizzle-orm/sqlite-core";

const TEXT_PREFIX = "enc:v1:";
const BLOB_MAGIC = Buffer.from("TPE1");

interface Keys {
  random: Buffer;
  deterministic: Buffer;
  ivKey: Buffer;
}

let cached: { raw: string; keys: Keys | null } | null = null;

function keys(): Keys | null {
  const raw = process.env.DATA_ENCRYPTION_KEY?.trim() ?? "";
  if (cached && cached.raw === raw) return cached.keys;
  let result: Keys | null = null;
  if (raw) {
    const master = Buffer.from(raw, "base64");
    if (master.length !== 32) throw new Error("DATA_ENCRYPTION_KEY must be 32 bytes, base64-encoded.");
    const derive = (info: string) => Buffer.from(hkdfSync("sha256", master, Buffer.alloc(0), info, 32));
    result = { random: derive("trip-planner/field/random"), deterministic: derive("trip-planner/field/deterministic"), ivKey: derive("trip-planner/field/iv") };
  }
  cached = { raw, keys: result };
  return result;
}

export function encryptionEnabled(): boolean {
  return keys() !== null;
}

function seal(key: Buffer, iv: Buffer, plain: Buffer): Buffer {
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const body = Buffer.concat([cipher.update(plain), cipher.final()]);
  return Buffer.concat([iv, body, cipher.getAuthTag()]);
}

function open(key: Buffer, sealed: Buffer): Buffer {
  const iv = sealed.subarray(0, 12);
  const tag = sealed.subarray(sealed.length - 16);
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(sealed.subarray(12, sealed.length - 16)), decipher.final()]);
}

function missingKey(): never {
  throw new Error("This data is encrypted but DATA_ENCRYPTION_KEY is not set.");
}

export function encryptText(plain: string, deterministic = false): string {
  const k = keys();
  if (!k) return plain;
  const data = Buffer.from(plain, "utf8");
  const iv = deterministic ? createHmac("sha256", k.ivKey).update(data).digest().subarray(0, 12) : randomBytes(12);
  return TEXT_PREFIX + seal(deterministic ? k.deterministic : k.random, iv, data).toString("base64url");
}

export function decryptText(stored: string, deterministic = false): string {
  if (!stored.startsWith(TEXT_PREFIX)) return stored;
  const k = keys() ?? missingKey();
  return open(deterministic ? k.deterministic : k.random, Buffer.from(stored.slice(TEXT_PREFIX.length), "base64url")).toString("utf8");
}

export const isEncryptedText = (value: unknown) => typeof value === "string" && value.startsWith(TEXT_PREFIX);

export function encryptBlob(plain: Buffer): Buffer {
  const k = keys();
  if (!k) return plain;
  return Buffer.concat([BLOB_MAGIC, seal(k.random, randomBytes(12), plain)]);
}

export function decryptBlob(stored: Buffer): Buffer {
  if (stored.length < BLOB_MAGIC.length + 28 || !stored.subarray(0, 4).equals(BLOB_MAGIC)) return stored;
  const k = keys() ?? missingKey();
  return open(k.random, stored.subarray(4));
}

export const isEncryptedBlob = (value: unknown) => Buffer.isBuffer(value) && value.length >= 4 && value.subarray(0, 4).equals(BLOB_MAGIC);

const toBuffer = (value: unknown): Buffer =>
  Buffer.isBuffer(value) ? value : value instanceof ArrayBuffer ? Buffer.from(value) : Buffer.from(value as Uint8Array);

/** Text encrypted with a random IV. */
export const encText = customType<{ data: string; driverData: string }>({
  dataType: () => "text",
  toDriver: (value) => encryptText(value),
  fromDriver: (value) => decryptText(String(value)),
});

/** Text encrypted deterministically, so equality lookups and UNIQUE still work. Use only where a lookup needs it. */
export const encLookupText = customType<{ data: string; driverData: string }>({
  dataType: () => "text",
  toDriver: (value) => encryptText(value, true),
  fromDriver: (value) => decryptText(String(value), true),
});

/** A number stored as encrypted text. The column keeps its REAL type; SQLite stores the text as is. */
export const encReal = customType<{ data: number; driverData: string | number }>({
  dataType: () => "real",
  toDriver: (value) => (encryptionEnabled() ? encryptText(String(value)) : value),
  fromDriver: (value) => (typeof value === "number" ? value : Number(decryptText(String(value)))),
});

export const encBlob = customType<{ data: Buffer; driverData: Buffer }>({
  dataType: () => "blob",
  toDriver: (value) => encryptBlob(value),
  fromDriver: (value) => decryptBlob(toBuffer(value)),
});
