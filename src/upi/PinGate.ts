import { getRandomBytes } from 'expo-crypto';
import { hasPin } from '../data/AppSettings';
import { loadSettings, updateSettings } from '../data/SettingsRepository';
import { hashPin, newSalt, verifyPin } from './PinHasher';

/**
 * Checks the driver PIN with a persisted lockout. The unlocked state itself is kept by each
 * screen, so it lasts only while that screen is open.
 */
const FREE_ATTEMPTS = 4;
const BASE_LOCK_MS = 30_000;

/** Milliseconds until another attempt is allowed, or 0. */
export async function lockRemainingMs(): Promise<number> {
  const d = (await loadSettings()).driver;
  return Math.max(d.pinLockedUntilEpochMs - Date.now(), 0);
}

/** Wrong attempts beyond FREE_ATTEMPTS lock the gate for a doubling delay. The counter survives restarts. */
export async function tryUnlock(pin: string): Promise<boolean> {
  const d = (await loadSettings()).driver;
  if (!hasPin(d) || (await lockRemainingMs()) > 0) return false;
  const ok = await verifyPin(pin, d.pinSalt, d.pinHash);
  await updateSettings((s) => {
    const attempts = ok ? 0 : s.driver.failedPinAttempts + 1;
    const lockMs = attempts > FREE_ATTEMPTS ? BASE_LOCK_MS * 2 ** Math.min(attempts - FREE_ATTEMPTS - 1, 8) : 0;
    return { ...s, driver: { ...s.driver, failedPinAttempts: attempts, pinLockedUntilEpochMs: Date.now() + lockMs } };
  });
  return ok;
}

/** Changing the PIN writes the new hash in one step; there is never a moment with no PIN. */
export async function setPin(pin: string): Promise<void> {
  const salt = newSalt(getRandomBytes);
  const hash = await hashPin(pin, salt);
  await updateSettings((s) => ({
    ...s,
    driver: { ...s.driver, pinHash: hash, pinSalt: salt, failedPinAttempts: 0, pinLockedUntilEpochMs: 0 },
  }));
}
