import { FareCalculator, toPaise } from '../../src/fare/FareCalculator';
import { FareConfig, isValidFare, MAX_KM, MAX_MULTIPLIER, MAX_RUPEES } from '../../src/fare/FareConfig';
import { estimateFare } from '../../src/fare/FareEstimator';
import { FarePresets } from '../../src/fare/FarePresets';
import { nightApplies, nightContains } from '../../src/fare/NightWindow';
import { defaultGpsFilterParams } from '../../src/tracking/GpsDistanceFilter';
import { defaultMeterParams, isValidMeterParams } from '../../src/tracking/TripAccumulator';

const at = (h: number, m: number) => new Date(2026, 0, 1, h, m);
const plusMinutes = (d: Date, m: number) => new Date(d.getTime() + m * 60_000);

/** TN tariff notified 27 Oct 2026: ₹50 / 1.8 km, ₹25/km, ₹1/min waiting, night ×1.5 (22:00–05:00). */
describe('TN 2026 tariff', () => {
  const cfg = FarePresets.TN_2026.config;
  const day = new Date(2026, 9, 28, 14, 0);
  const night = new Date(2026, 9, 28, 23, 0);
  const fare = (km: number, waitMin: number, start: Date, c: FareConfig = cfg) =>
    FareCalculator.calculateForTimes(c, km * 1000, waitMin * 60, start, plusMinutes(start, 20));

  test('is the default preset', () => {
    expect(FarePresets.default).toBe(FarePresets.TN_2026);
    expect(isValidFare(cfg)).toBe(true);
  });

  test('5 km with 10 min waiting by day is 140', () => {
    const f = fare(5, 10, day);
    expect(f.minFare.toFixed(2)).toBe('50.00');
    expect(f.extraDistanceCharge.toFixed(2)).toBe('80.00'); // 3.2 km × 25
    expect(f.waitingBlocks).toBe(10);
    expect(f.waitingCharge.toFixed(2)).toBe('10.00');
    expect(f.subtotal.toFixed(2)).toBe('140.00');
    expect(f.total.toFixed()).toBe('140');
  });

  test('5 km with 10 min waiting at night is 205', () => {
    const f = fare(5, 10, night);
    expect(f.nightSurcharge.toFixed(2)).toBe('65.00'); // (50 + 80) × 0.5
    expect(f.total.toFixed()).toBe('205');
  });

  test('minimum distance and steps', () => {
    expect(fare(1.8, 0, day).total.toFixed()).toBe('50');
    expect(fare(1.9, 0, day).subtotal.toFixed(2)).toBe('52.50'); // one 100 m step = ₹2.50
    expect(fare(1.9, 0, day).total.toFixed()).toBe('53');
  });

  test('waiting is charged per completed minute', () => {
    const f = FareCalculator.calculate(cfg, 1000, 4 * 60 + 59, false);
    expect(f.waitingBlocks).toBe(4);
    expect(f.total.toFixed()).toBe('54');
  });

  test('ghat road preset', () => {
    expect(fare(5, 0, day, FarePresets.TN_2026_GHAT.config).total.toFixed()).toBe('156'); // 60 + 3.2 × 30
  });

  test('estimate gives day and night ranges with 10% route variance', () => {
    const e = estimateFare(cfg, 5, 10);
    expect(e.day.low.toFixed()).toBe('140');
    expect(e.day.high.toFixed()).toBe('153'); // 5.5 km: 50 + 37 steps × 2.50 + 10 = 152.50, half up
    expect(e.night.low.toFixed()).toBe('205');
  });
});

describe('FareConfig bounds', () => {
  const base = FarePresets.default.config;

  test('non finite and oversized values are invalid', () => {
    expect(isValidFare({ ...base, minFare: Number.POSITIVE_INFINITY })).toBe(false);
    expect(isValidFare({ ...base, minFare: Number.NaN })).toBe(false);
    expect(isValidFare({ ...base, minFare: 1e20 })).toBe(false);
    expect(isValidFare({ ...base, minDistanceKm: 5_000 })).toBe(false);
    expect(isValidFare({ ...base, nightMultiplier: 100 })).toBe(false);
    expect(isValidFare({ ...base, distanceStepMeters: 2.5 })).toBe(false);
    expect(isValidFare(base)).toBe(true);
    expect(isValidFare({ ...base, minFare: MAX_RUPEES })).toBe(true);
  });

  test('largest allowed fare still fits in paise', () => {
    const f = FareCalculator.calculate(
      { ...base, minFare: MAX_RUPEES, perKmRate: MAX_RUPEES, waitingRate: MAX_RUPEES, waitingBlockMinutes: 1, nightMultiplier: MAX_MULTIPLIER },
      MAX_KM * 1000,
      1440 * 60,
      true,
    );
    expect(() => toPaise(f.total)).not.toThrow();
  });

  test('meter params bounds', () => {
    expect(isValidMeterParams(defaultMeterParams())).toBe(true);
    expect(isValidMeterParams(defaultMeterParams({ autoWaitSpeedKmh: Number.NaN }))).toBe(false);
    expect(isValidMeterParams(defaultMeterParams({ gps: defaultGpsFilterParams({ maxAccuracyM: Number.POSITIVE_INFINITY }) }))).toBe(false);
  });
});

describe('NightWindow', () => {
  const cfg = FarePresets.TN_2013.config;

  test('window wraps past midnight with inclusive start and exclusive end', () => {
    expect(nightContains(cfg, at(21, 59))).toBe(false);
    expect(nightContains(cfg, at(22, 0))).toBe(true);
    expect(nightContains(cfg, at(0, 0))).toBe(true);
    expect(nightContains(cfg, at(4, 59))).toBe(true);
    expect(nightContains(cfg, at(5, 0))).toBe(false);
    expect(nightContains(cfg, at(12, 0))).toBe(false);
  });

  test('non wrapping window', () => {
    const c = { ...cfg, nightStartMinutes: 60, nightEndMinutes: 240 };
    expect(nightContains(c, at(2, 0))).toBe(true);
    expect(nightContains(c, at(23, 0))).toBe(false);
  });

  test('equal start and end means no night window', () => {
    const c: FareConfig = { ...cfg, nightStartMinutes: 0, nightEndMinutes: 0, nightRule: 'ANY_PART' };
    expect(nightContains(c, at(0, 0))).toBe(false);
    expect(nightApplies(c, new Date(2026, 0, 1, 21, 0), new Date(2026, 0, 2, 6, 0))).toBe(false);
  });

  test('any part detects a window fully inside a long trip', () => {
    const c: FareConfig = { ...cfg, nightStartMinutes: 22 * 60, nightEndMinutes: 22 * 60 + 30, nightRule: 'ANY_PART' };
    const start = at(21, 50);
    expect(nightApplies(c, start, at(22, 40))).toBe(true);
    expect(nightApplies(c, start, plusMinutes(start, 5))).toBe(false);
  });
});
