import Decimal from 'decimal.js';
import { FareCalculator } from './FareCalculator';
import { FareConfig } from './FareConfig';

export interface FareRange {
  low: Decimal;
  high: Decimal;
}

export interface FareEstimate {
  day: FareRange;
  night: FareRange;
}

export const DEFAULT_VARIANCE = 0.1;

/** Pre-trip estimate; the high end allows for a route [variance] longer than entered. */
export function estimateFare(config: FareConfig, km: number, waitingMinutes: number, variance = DEFAULT_VARIANCE): FareEstimate {
  const meters = Math.max(km, 0) * 1000;
  const waitSec = Math.trunc(Math.max(waitingMinutes, 0) * 60);
  const range = (night: boolean): FareRange => ({
    low: FareCalculator.calculate(config, meters, waitSec, night).total,
    high: FareCalculator.calculate(config, meters * (1 + variance), waitSec, night).total,
  });
  return { day: range(false), night: range(true) };
}
