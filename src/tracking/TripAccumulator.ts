import { LatLng } from './Geo';
import { defaultGpsFilterParams, FilterResult, GpsDistanceFilter, GpsFilterParams, isValidGpsParams, LocationFix } from './GpsDistanceFilter';

export interface MeterParams {
  /** Below this speed the auto is considered stopped. */
  autoWaitSpeedKmh: number;
  /** Waiting accrues once the auto has been stopped continuously for longer than this. */
  autoWaitAfterSeconds: number;
  gps: GpsFilterParams;
}

export const defaultMeterParams = (overrides: Partial<MeterParams> = {}): MeterParams => ({
  autoWaitSpeedKmh: 5,
  autoWaitAfterSeconds: 60,
  gps: defaultGpsFilterParams(),
  ...overrides,
});

export const isValidMeterParams = (p: MeterParams): boolean =>
  p.autoWaitSpeedKmh >= 0 &&
  p.autoWaitSpeedKmh <= 1_000 &&
  Number.isInteger(p.autoWaitAfterSeconds) &&
  p.autoWaitAfterSeconds >= 0 &&
  p.autoWaitAfterSeconds <= 86_400 &&
  isValidGpsParams(p.gps);

export interface TripAccumulatorState {
  distanceMeters?: number;
  movingMs?: number;
  waitingMs?: number;
  manualWait?: boolean;
}

/**
 * Pure trip state machine: distance from the GPS filter, and moving vs waiting time.
 * All times are milliseconds on one clock supplied by the caller.
 */
export class TripAccumulator {
  private readonly filter: GpsDistanceFilter;
  private lastTickMs: number;
  private slowSinceMs: number | null = null;

  distanceMeters: number;
  movingMs: number;
  waitingMs: number;
  manualWait: boolean;

  /** Last position accepted by the filter. */
  lastPoint: LatLng | null = null;
  lastAccuracyM: number | null = null;
  lastFixMs: number | null = null;

  constructor(private readonly params: MeterParams, nowMs: number, restored: TripAccumulatorState = {}) {
    this.filter = new GpsDistanceFilter(params.gps);
    this.lastTickMs = nowMs;
    this.distanceMeters = restored.distanceMeters ?? 0;
    this.movingMs = restored.movingMs ?? 0;
    this.waitingMs = restored.waitingMs ?? 0;
    this.manualWait = restored.manualWait ?? false;
  }

  onFix(fix: LocationFix): FilterResult {
    const result = this.filter.process(fix);
    this.lastAccuracyM = fix.accuracyM;
    if (!result.accepted) return result;
    this.lastFixMs = fix.timeMs;
    this.distanceMeters += result.addedMeters;
    if (result.point) this.lastPoint = result.point;
    if (result.speedMps != null) {
      if (result.speedMps * 3.6 < this.params.autoWaitSpeedKmh) {
        if (this.slowSinceMs == null) this.slowSinceMs = fix.timeMs;
      } else {
        this.slowSinceMs = null;
      }
    }
    return result;
  }

  isAutoWaiting(nowMs: number): boolean {
    if (this.slowSinceMs == null) return false;
    return nowMs - this.slowSinceMs > this.params.autoWaitAfterSeconds * 1000;
  }

  /** Manual WAIT overrides auto detection. */
  isWaiting(nowMs: number): boolean {
    return this.manualWait || this.isAutoWaiting(nowMs);
  }

  /** Attributes the time since the previous tick to moving or waiting. */
  tick(nowMs: number): void {
    const dt = nowMs - this.lastTickMs;
    if (dt <= 0) return;
    if (this.isWaiting(nowMs)) this.waitingMs += dt;
    else this.movingMs += dt;
    this.lastTickMs = nowMs;
  }
}
