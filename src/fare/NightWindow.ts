import { FareConfig } from './FareConfig';

/** Minutes after local midnight. */
const minuteOfDay = (d: Date) => d.getHours() * 60 + d.getMinutes();

/** True when the local time of [time] is inside [start, end); the window may wrap past midnight. */
export function nightContains(config: FareConfig, time: Date): boolean {
  const m = minuteOfDay(time);
  const s = config.nightStartMinutes;
  const e = config.nightEndMinutes;
  if (s === e) return false;
  if (s < e) return m >= s && m < e;
  return m >= s || m < e;
}

/** Whether the night multiplier applies to a trip, according to config.nightRule. */
export function nightApplies(config: FareConfig, start: Date, end: Date): boolean {
  switch (config.nightRule) {
    case 'TRIP_START':
      return nightContains(config, start);
    case 'TRIP_END':
      return nightContains(config, end);
    case 'ANY_PART':
      return overlaps(config, start, end);
  }
}

function overlaps(config: FareConfig, start: Date, end: Date): boolean {
  if (nightContains(config, start) || nightContains(config, end)) return true;
  if (config.nightStartMinutes === config.nightEndMinutes) return false;
  // Neither endpoint is at night, so the trip overlaps only if a window opens inside it.
  const h = Math.floor(config.nightStartMinutes / 60);
  const m = config.nightStartMinutes % 60;
  const day = new Date(start.getFullYear(), start.getMonth(), start.getDate());
  const lastDay = new Date(end.getFullYear(), end.getMonth(), end.getDate());
  while (day.getTime() <= lastDay.getTime()) {
    const opening = new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, m);
    if (opening.getTime() >= start.getTime() && opening.getTime() <= end.getTime()) return true;
    day.setDate(day.getDate() + 1);
  }
  return false;
}
