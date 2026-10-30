import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { base64Decode, base64Encode } from '../util/base64';

/**
 * Salted PBKDF2-HMAC-SHA256 for the 4-digit driver-mode PIN. The PIN is never stored in clear.
 * Same parameters and base64 encoding as the original Android app.
 */
const ITERATIONS = 20_000;
const KEY_BYTES = 32;

export function isValidPin(pin: string): boolean {
  return /^[0-9]{4}$/.test(pin);
}

/** [random] is injectable for tests; the app passes expo-crypto's secure generator. */
export function newSalt(random: (bytes: number) => Uint8Array): string {
  return base64Encode(random(16));
}

export async function hashPin(pin: string, salt: string): Promise<string> {
  const key = await pbkdf2Async(sha256, new TextEncoder().encode(pin), base64Decode(salt), { c: ITERATIONS, dkLen: KEY_BYTES });
  return base64Encode(key);
}

export async function verifyPin(pin: string, salt: string, expectedHash: string): Promise<boolean> {
  return constantTimeEquals(await hashPin(pin, salt), expectedHash);
}

function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
