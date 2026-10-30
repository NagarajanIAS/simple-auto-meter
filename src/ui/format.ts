import Decimal from 'decimal.js';
import { currentLocaleTag } from '../i18n';

const pad2 = (n: number) => String(n).padStart(2, '0');

/** Whole rupees when there are no paise, otherwise two decimals. */
export function formatRupees(amount: Decimal): string {
  return '₹' + (amount.isInteger() ? amount.toFixed(0) : amount.toFixed(2, Decimal.ROUND_HALF_UP));
}

/** Always two decimals, e.g. ₹38.40 — used in breakdowns. */
export const formatRupeesExact = (amount: Decimal): string => '₹' + amount.toFixed(2, Decimal.ROUND_HALF_UP);

export const formatKm = (meters: number): string => `${(meters / 1000).toFixed(2)} km`;

export function formatDuration(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  return h > 0 ? `${h}:${pad2(m)}:${pad2(s)}` : `${pad2(m)}:${pad2(s)}`;
}

export const formatDateTime = (epochMs: number): string =>
  new Date(epochMs).toLocaleString(currentLocaleTag(), { dateStyle: 'medium', timeStyle: 'short' });

export const formatDate = (date: Date): string => date.toLocaleDateString(currentLocaleTag(), { dateStyle: 'medium' });

/** ISO date string (yyyy-mm-dd) from a preset → localized display, or null. */
export function tariffEffectiveText(iso: string | null | undefined): string | null {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null;
  return m ? formatDate(new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))) : null;
}

/** dd-MM-yyyy HH:mm, used in the UPI payment note. */
export function formatUpiNoteTime(d: Date): string {
  return `${pad2(d.getDate())}-${pad2(d.getMonth() + 1)}-${d.getFullYear()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

export const minutesToHhMm = (minutes: number): string => `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`;

export function hhMmToMinutes(text: string): number | null {
  const m = /^(\d+):(\d+)$/.exec(text.trim());
  if (!m) return null;
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 23 || min > 59) return null;
  return h * 60 + min;
}
