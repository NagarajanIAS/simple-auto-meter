/** How the final amount is rounded to whole rupees. */
export type FareRounding = 'NEAREST' | 'UP';

/** Which moment(s) of the trip decide whether the night multiplier applies. */
export type NightRule =
  /** Surcharge if the trip starts inside the night window (default). */
  | 'TRIP_START'
  /** Surcharge if the trip ends inside the night window. */
  | 'TRIP_END'
  /** Surcharge if any part of the trip overlaps the night window. */
  | 'ANY_PART';

/**
 * Every fare parameter. Nothing about fares is hard-coded in the calculator;
 * presets in FarePresets are just named instances of this type.
 */
export interface FareConfig {
  /** Fixed charge covering the first minDistanceKm. */
  minFare: number;
  minDistanceKm: number;
  /** Rate per km after the minimum distance. */
  perKmRate: number;
  /** Meter tick after the minimum distance; each completed step costs perKmRate × step / 1000. */
  distanceStepMeters: number;
  /** Charge per completed waiting block. */
  waitingRate: number;
  waitingBlockMinutes: number;
  nightMultiplier: number;
  /** Minutes after midnight; the window may wrap past midnight (22:00 → 05:00). */
  nightStartMinutes: number;
  nightEndMinutes: number;
  nightAppliesToWaiting: boolean;
  nightRule: NightRule;
  rounding: FareRounding;
}

export const MINUTES_PER_DAY = 24 * 60;
// Upper bounds keep every intermediate amount far inside safe-integer paise and reject NaN/Infinity
// (a range check on NaN is false, so NaN fails too).
export const MAX_RUPEES = 100_000;
export const MAX_KM = 1_000;
export const MAX_MULTIPLIER = 10;

const inRange = (v: number, lo: number, hi: number) => v >= lo && v <= hi;
const isInt = (v: number) => Number.isInteger(v);

/** Returns a list of human-readable problems; empty when the config is usable. */
export function fareProblems(c: FareConfig): string[] {
  const p: string[] = [];
  if (!inRange(c.minFare, 0, MAX_RUPEES)) p.push(`minFare must be 0–${MAX_RUPEES}`);
  if (!inRange(c.minDistanceKm, 0, MAX_KM)) p.push(`minDistanceKm must be 0–${MAX_KM}`);
  if (!inRange(c.perKmRate, 0, MAX_RUPEES)) p.push(`perKmRate must be 0–${MAX_RUPEES}`);
  if (!isInt(c.distanceStepMeters) || !inRange(c.distanceStepMeters, 1, 100_000)) p.push('distanceStepMeters must be 1–100000');
  if (!inRange(c.waitingRate, 0, MAX_RUPEES)) p.push(`waitingRate must be 0–${MAX_RUPEES}`);
  if (!isInt(c.waitingBlockMinutes) || !inRange(c.waitingBlockMinutes, 1, 1440)) p.push('waitingBlockMinutes must be 1–1440');
  if (!inRange(c.nightMultiplier, 1, MAX_MULTIPLIER)) p.push(`nightMultiplier must be 1–${MAX_MULTIPLIER}`);
  if (!isInt(c.nightStartMinutes) || !inRange(c.nightStartMinutes, 0, MINUTES_PER_DAY - 1)) p.push('nightStart out of range');
  if (!isInt(c.nightEndMinutes) || !inRange(c.nightEndMinutes, 0, MINUTES_PER_DAY - 1)) p.push('nightEnd out of range');
  return p;
}

export const isValidFare = (c: FareConfig): boolean => fareProblems(c).length === 0;
