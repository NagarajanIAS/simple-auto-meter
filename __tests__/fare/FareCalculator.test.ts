import { FareCalculator, paiseToRupees, toPaise, Money } from '../../src/fare/FareCalculator';
import { FareConfig } from '../../src/fare/FareConfig';
import { FarePresets } from '../../src/fare/FarePresets';

const tn2013 = FarePresets.TN_2013.config;
const day = new Date(2026, 9, 28, 14, 0);
const night = new Date(2026, 9, 28, 23, 0);
const plusMinutes = (d: Date, m: number) => new Date(d.getTime() + m * 60_000);

function fare(km: number, opts: { waitMin?: number; start?: Date; end?: Date; config?: FareConfig } = {}) {
  const start = opts.start ?? day;
  return FareCalculator.calculateForTimes(opts.config ?? tn2013, km * 1000, (opts.waitMin ?? 0) * 60, start, opts.end ?? plusMinutes(start, 20));
}

// BigDecimal-style comparison: value and number of decimals must both match.
const str = (d: { toFixed: () => string }) => d.toFixed();

describe('FareCalculator (TN 2013)', () => {
  test('5 km with 10 min waiting by day is 70', () => {
    const f = fare(5, { waitMin: 10 });
    expect(f.minFare.toFixed(2)).toBe('25.00');
    expect(f.chargedExtraKm.toFixed(3)).toBe('3.200');
    expect(f.extraDistanceCharge.toFixed(2)).toBe('38.40');
    expect(f.waitingBlocks).toBe(2);
    expect(f.waitingCharge.toFixed(2)).toBe('7.00');
    expect(f.isNight).toBe(false);
    expect(f.nightSurcharge.toFixed(2)).toBe('0.00');
    expect(f.subtotal.toFixed(2)).toBe('70.40');
    expect(str(f.total)).toBe('70');
  });

  test('5 km with 10 min waiting at night multiplies distance only, 102', () => {
    const f = fare(5, { waitMin: 10, start: night });
    expect(f.isNight).toBe(true);
    // (25 + 38.40) × 0.5 = 31.70 surcharge; waiting not multiplied.
    expect(f.nightSurcharge.toFixed(2)).toBe('31.70');
    expect(f.subtotal.toFixed(2)).toBe('102.10');
    expect(str(f.total)).toBe('102');
  });

  test('night multiplier on waiting when enabled', () => {
    const f = fare(5, { waitMin: 10, start: night, config: { ...tn2013, nightAppliesToWaiting: true } });
    expect(f.nightSurcharge.toFixed(2)).toBe('35.20'); // (63.40 + 7.00) × 0.5
    expect(f.subtotal.toFixed(2)).toBe('105.60');
    expect(str(f.total)).toBe('106');
  });

  test('trip shorter than minimum distance pays minimum fare', () => {
    for (const km of [0, 0.3, 1, 1.79]) {
      const f = fare(km);
      expect(f.extraDistanceCharge.toFixed(2)).toBe('0.00');
      expect(str(f.total)).toBe('25');
    }
  });

  test('exactly the minimum distance pays minimum fare', () => {
    const f = fare(1.8);
    expect(f.chargedExtraKm.toFixed(3)).toBe('0.000');
    expect(f.subtotal.toFixed(2)).toBe('25.00');
    expect(str(f.total)).toBe('25');
  });

  test('partial distance step is not charged', () => {
    // 1.899 km → 99 m extra → no completed 100 m step.
    expect(fare(1.899).subtotal.toFixed(2)).toBe('25.00');
    // 1.9 km → one step of 100 m = ₹1.20.
    expect(fare(1.9).subtotal.toFixed(2)).toBe('26.20');
    expect(str(fare(1.9).total)).toBe('26');
  });

  test('step size is configurable', () => {
    const config = { ...tn2013, distanceStepMeters: 1000 };
    expect(fare(2.7, { config }).subtotal.toFixed(2)).toBe('25.00'); // 900 m extra, no full km
    expect(fare(2.8, { config }).subtotal.toFixed(2)).toBe('37.00');
  });

  test('only completed waiting blocks are charged', () => {
    expect(FareCalculator.calculate(tn2013, 1000, 4 * 60 + 59, false).waitingBlocks).toBe(0);
    const g = FareCalculator.calculate(tn2013, 1000, 14 * 60 + 59, false);
    expect(g.waitingBlocks).toBe(2);
    expect(g.subtotal.toFixed(2)).toBe('32.00');
  });

  test('rounding nearest and up', () => {
    // 2.2 km → 4 steps × 1.20 = 4.80 → 29.80
    expect(str(fare(2.2).total)).toBe('30');
    // 2.0 km → 2.40 → 27.40
    expect(str(fare(2.0).total)).toBe('27');
    expect(str(fare(2.0, { config: { ...tn2013, rounding: 'UP' } }).total)).toBe('28');
    // .50 rounds half up
    const half = FareCalculator.calculate({ ...tn2013, perKmRate: 5 }, 1900, 0, false);
    expect(half.subtotal.toFixed(2)).toBe('25.50');
    expect(str(half.total)).toBe('26');
  });

  test('round up leaves whole rupees alone', () => {
    expect(str(fare(1.0, { config: { ...tn2013, rounding: 'UP' } }).total)).toBe('25');
  });

  test('trip starting at night and ending in day is night under TRIP_START', () => {
    const start = new Date(2026, 9, 28, 4, 50);
    const end = new Date(2026, 9, 28, 5, 20);
    expect(fare(5, { start, end }).isNight).toBe(true);
    expect(fare(5, { start, end, config: { ...tn2013, nightRule: 'TRIP_END' } }).isNight).toBe(false);
  });

  test('trip starting in day and ending at night is day under TRIP_START', () => {
    const start = new Date(2026, 9, 28, 21, 45);
    const end = new Date(2026, 9, 28, 22, 15);
    const f = fare(5, { waitMin: 10, start, end });
    expect(f.isNight).toBe(false);
    expect(str(f.total)).toBe('70');
    expect(fare(5, { start, end, config: { ...tn2013, nightRule: 'TRIP_END' } }).isNight).toBe(true);
    expect(fare(5, { start, end, config: { ...tn2013, nightRule: 'ANY_PART' } }).isNight).toBe(true);
  });

  test('trip crossing midnight inside night window', () => {
    expect(fare(5, { start: new Date(2026, 9, 28, 23, 50), end: new Date(2026, 9, 29, 0, 20) }).isNight).toBe(true);
  });

  test('negative inputs are treated as zero', () => {
    expect(str(FareCalculator.calculate(tn2013, -50, -10, false).total)).toBe('25');
  });

  test('all components add up to subtotal', () => {
    for (const km of [0.5, 1.8, 3.33, 7.77, 12, 25.4])
      for (const w of [0, 4, 5, 23])
        for (const n of [false, true]) {
          const f = FareCalculator.calculate(tn2013, km * 1000, w * 60, n);
          expect(f.subtotal.equals(f.minFare.plus(f.extraDistanceCharge).plus(f.waitingCharge).plus(f.nightSurcharge))).toBe(true);
        }
  });

  test('invalid config is rejected', () => {
    expect(() => FareCalculator.calculate({ ...tn2013, distanceStepMeters: 0 }, 1000, 0, false)).toThrow(RangeError);
  });

  test('paise conversion round trips', () => {
    expect(toPaise(new Money('70.40'))).toBe(7040);
    expect(paiseToRupees(7040).toFixed(2)).toBe('70.40');
  });
});
