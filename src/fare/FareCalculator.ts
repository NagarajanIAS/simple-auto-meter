import Decimal from 'decimal.js';
import { FareConfig, fareProblems } from './FareConfig';
import { nightApplies } from './NightWindow';

// Exact decimal arithmetic, like java.math.BigDecimal. Decimal(number) uses the number's shortest
// decimal form (0.1 → "0.1"), matching BigDecimal.valueOf(double).
const D = Decimal.clone({ precision: 64, rounding: Decimal.ROUND_HALF_UP });
type D = Decimal;

/** Itemised fare. Money values are exact rupees (2 decimals) except total, which is rounded. */
export interface FareBreakdown {
  minFare: D;
  /** Distance actually charged beyond the minimum, in whole meter steps (3 decimals). */
  chargedExtraKm: D;
  extraDistanceCharge: D;
  waitingBlocks: number;
  waitingCharge: D;
  isNight: boolean;
  nightSurcharge: D;
  /** Sum of all components before rounding. */
  subtotal: D;
  /** Amount payable, in whole rupees. */
  total: D;
}

const THOUSAND = new D(1000);

const money = (value: number): D => new D(value).toDecimalPlaces(2, D.ROUND_HALF_UP);

export const FareCalculator = {
  /** Night is decided from the trip's start/end times according to the config's night rule. */
  calculateForTimes(config: FareConfig, distanceMeters: number, waitingSeconds: number, start: Date, end: Date): FareBreakdown {
    return FareCalculator.calculate(config, distanceMeters, waitingSeconds, nightApplies(config, start, end));
  },

  calculate(config: FareConfig, distanceMeters: number, waitingSeconds: number, isNight: boolean): FareBreakdown {
    const problems = fareProblems(config);
    if (problems.length > 0) throw new RangeError(`Invalid fare config: ${problems.join(', ')}`);

    const minFare = money(config.minFare);

    // Distance beyond the minimum, charged per completed step.
    const minMeters = new D(config.minDistanceKm).times(THOUSAND);
    const extraMeters = D.max(new D(Math.max(distanceMeters, 0)).minus(minMeters), 0);
    const step = new D(config.distanceStepMeters);
    const steps = extraMeters.dividedToIntegerBy(step);
    const chargedExtraMeters = steps.times(step);
    const extraDistanceCharge = new D(config.perKmRate)
      .times(chargedExtraMeters)
      .dividedBy(THOUSAND)
      .toDecimalPlaces(2, D.ROUND_HALF_UP);

    // Waiting: only completed blocks.
    const blockSeconds = config.waitingBlockMinutes * 60;
    const waitingBlocks = Math.floor(Math.max(Math.trunc(waitingSeconds), 0) / blockSeconds);
    const waitingCharge = money(config.waitingRate).times(waitingBlocks);

    let nightSurcharge = new D(0);
    if (isNight) {
      const extraFactor = new D(config.nightMultiplier).minus(1);
      let base = minFare.plus(extraDistanceCharge);
      if (config.nightAppliesToWaiting) base = base.plus(waitingCharge);
      nightSurcharge = base.times(extraFactor).toDecimalPlaces(2, D.ROUND_HALF_UP);
    }

    const subtotal = minFare.plus(extraDistanceCharge).plus(waitingCharge).plus(nightSurcharge).toDecimalPlaces(2, D.ROUND_HALF_UP);
    const total = subtotal.toDecimalPlaces(0, config.rounding === 'UP' ? D.ROUND_CEIL : D.ROUND_HALF_UP);

    return {
      minFare,
      chargedExtraKm: chargedExtraMeters.dividedBy(THOUSAND),
      extraDistanceCharge,
      waitingBlocks,
      waitingCharge: waitingCharge.toDecimalPlaces(2, D.ROUND_HALF_UP),
      isNight,
      nightSurcharge,
      subtotal,
      total,
    };
  },
};

/** Converts an exact rupee amount to paise for storage. */
export function toPaise(amount: Decimal): number {
  const paise = amount.times(100).toDecimalPlaces(0, Decimal.ROUND_HALF_UP).toNumber();
  if (!Number.isSafeInteger(paise)) throw new RangeError('Amount out of range');
  return paise;
}

export const paiseToRupees = (paise: number): Decimal => new D(paise).dividedBy(100);

export { D as Money };
