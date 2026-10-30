import { haversineMeters, LatLng } from './Geo';

/** A single GPS fix, decoupled from expo-location so it can be unit-tested. */
export interface LocationFix {
  lat: number;
  lng: number;
  accuracyM: number;
  /** Timestamp in milliseconds; must share a clock with the meter's tick time. */
  timeMs: number;
  /** Speed reported by the receiver, if any. */
  speedMps?: number | null;
}

export interface GpsFilterParams {
  maxAccuracyM: number;
  maxSpeedKmh: number;
  minSegmentM: number;
  /** Segment threshold = max(minSegmentM, accuracyFactor × fix accuracy). */
  accuracyFactor: number;
  smoothing: boolean;
}

export const defaultGpsFilterParams = (overrides: Partial<GpsFilterParams> = {}): GpsFilterParams => ({
  maxAccuracyM: 25,
  maxSpeedKmh: 90,
  minSegmentM: 5,
  accuracyFactor: 1,
  smoothing: true,
  ...overrides,
});

const inRange = (v: number, lo: number, hi: number) => v >= lo && v <= hi;

export const isValidGpsParams = (p: GpsFilterParams): boolean =>
  inRange(p.maxAccuracyM, 1, 10_000) &&
  inRange(p.maxSpeedKmh, 1, 1_000) &&
  inRange(p.minSegmentM, 0, 10_000) &&
  inRange(p.accuracyFactor, 0, 100);

export type RejectReason = 'INACCURATE' | 'OUT_OF_ORDER' | 'JUMP';

export interface FilterResult {
  accepted: boolean;
  addedMeters: number;
  /** Position used for distance (smoothed when enabled). */
  point: LatLng | null;
  speedMps: number | null;
  rejectReason: RejectReason | null;
}

const RESEED_AFTER_JUMPS = 5;
const SPEED_WINDOW_MS = 10_000;
const MIN_SPEED_WINDOW_S = 3;

const accept = (point: LatLng | null, speedMps: number | null, addedMeters = 0): FilterResult => ({
  accepted: true,
  addedMeters,
  point,
  speedMps,
  rejectReason: null,
});

const reject = (reason: RejectReason): FilterResult => ({
  accepted: false,
  addedMeters: 0,
  point: null,
  speedMps: null,
  rejectReason: reason,
});

const fixLatLng = (f: LocationFix): LatLng => ({ lat: f.lat, lng: f.lng });

/**
 * Turns noisy GPS fixes into a distance that does not creep while stationary.
 *
 * Distance is measured from an anchor point; the anchor only moves once the vehicle
 * has moved further than the noise threshold, so drift around a stopped auto adds nothing
 * while slow genuine movement is still counted once it exceeds the threshold.
 */
export class GpsDistanceFilter {
  private lastGood: LocationFix | null = null;
  private anchor: LatLng | null = null;
  private consecutiveJumps = 0;
  private readonly smoother = new PositionSmoother();
  private recent: { t: number; p: LatLng }[] = [];

  constructor(private readonly params: GpsFilterParams = defaultGpsFilterParams()) {}

  process(fix: LocationFix): FilterResult {
    if (!(fix.accuracyM <= this.params.maxAccuracyM)) return reject('INACCURATE');

    const prev = this.lastGood;
    if (prev) {
      const dtSec = (fix.timeMs - prev.timeMs) / 1000;
      if (dtSec <= 0) return reject('OUT_OF_ORDER');
      const jumpSpeed = haversineMeters(fixLatLng(prev), fixLatLng(fix)) / dtSec;
      if (jumpSpeed * 3.6 > this.params.maxSpeedKmh) {
        this.consecutiveJumps++;
        if (this.consecutiveJumps < RESEED_AFTER_JUMPS) return reject('JUMP');
        // The earlier fix was probably the outlier: start again from here, counting nothing.
        this.reseed(fix);
        return accept(this.anchor, fix.speedMps ?? null);
      }
    }
    this.consecutiveJumps = 0;
    this.lastGood = fix;

    const point = this.params.smoothing ? this.smoother.process(fix) : fixLatLng(fix);
    // Displacement over a few seconds decides moving vs stopped; the receiver's own speed
    // is only a fallback until that window fills (it can read ~5 km/h while parked).
    const speed = this.windowSpeed(fix.timeMs, point) ?? fix.speedMps ?? null;

    const from = this.anchor;
    if (!from) {
      this.anchor = point;
      return accept(point, speed);
    }
    const segment = haversineMeters(from, point);
    const threshold = Math.max(this.params.minSegmentM, this.params.accuracyFactor * fix.accuracyM);
    if (segment < threshold) return accept(point, speed);

    this.anchor = point;
    return accept(point, speed, segment);
  }

  reset(): void {
    this.lastGood = null;
    this.anchor = null;
    this.consecutiveJumps = 0;
    this.smoother.reset();
    this.recent = [];
  }

  /**
   * Speed from displacement over the last few seconds. Fix-to-fix speed is dominated by jitter
   * (±2 m per second reads as ~10 km/h); a 10 s window of smoothed positions is not.
   */
  private windowSpeed(timeMs: number, point: LatLng): number | null {
    this.recent.push({ t: timeMs, p: point });
    while (this.recent.length > 1 && timeMs - this.recent[0].t > SPEED_WINDOW_MS) this.recent.shift();
    const { t: t0, p: p0 } = this.recent[0];
    const dtSec = (timeMs - t0) / 1000;
    return dtSec >= MIN_SPEED_WINDOW_S ? haversineMeters(p0, point) / dtSec : null;
  }

  private reseed(fix: LocationFix): void {
    this.reset();
    this.lastGood = fix;
    this.anchor = this.params.smoothing ? this.smoother.process(fix) : fixLatLng(fix);
  }
}

/**
 * Minimal 1-D Kalman filter on latitude/longitude, weighting each fix by its reported accuracy.
 * Process noise qMetresPerSecond is the expected speed uncertainty.
 */
export class PositionSmoother {
  private lat = 0;
  private lng = 0;
  private timeMs = 0;
  private variance = -1;

  constructor(private readonly qMetresPerSecond = 3) {}

  process(fix: LocationFix): LatLng {
    const accuracy = Math.max(fix.accuracyM, 1);
    if (this.variance < 0) {
      this.lat = fix.lat;
      this.lng = fix.lng;
      this.timeMs = fix.timeMs;
      this.variance = accuracy * accuracy;
    } else {
      const dt = fix.timeMs - this.timeMs;
      if (dt > 0) {
        this.variance += (dt * this.qMetresPerSecond * this.qMetresPerSecond) / 1000;
        this.timeMs = fix.timeMs;
      }
      const k = this.variance / (this.variance + accuracy * accuracy);
      this.lat += k * (fix.lat - this.lat);
      this.lng += k * (fix.lng - this.lng);
      this.variance *= 1 - k;
    }
    return { lat: this.lat, lng: this.lng };
  }

  reset(): void {
    this.variance = -1;
  }
}
