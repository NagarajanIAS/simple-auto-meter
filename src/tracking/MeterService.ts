import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { FareBreakdown, FareCalculator } from '../fare/FareCalculator';
import { FareConfig } from '../fare/FareConfig';
import { activeFareConfig, activePreset } from '../data/AppSettings';
import { LiveTripStore } from '../data/LiveTripStore';
import { loadSettings } from '../data/SettingsRepository';
import { TripRepository } from '../data/TripRepository';
import { t } from '../i18n';
import { Store } from '../util/Store';
import { LatLng } from './Geo';
import { TripAccumulator } from './TripAccumulator';

/**
 * Runs the meter. GPS comes from an expo-location task backed by an Android foreground service
 * (type "location") with a notification, so it keeps counting with the screen off. It is only ever
 * started while the app is visible, so background location permission is not needed.
 */

export interface LiveMeter {
  running: boolean;
  startEpochMs: number;
  distanceMeters: number;
  movingMs: number;
  waitingMs: number;
  manualWait: boolean;
  autoWaiting: boolean;
  fare: FareBreakdown | null;
  tariffName: string;
  tariffEffective: string | null;
  /** False when there has been no usable GPS fix recently. */
  gpsOk: boolean;
  accuracyM: number | null;
  position: LatLng | null;
  track: LatLng[];
}

const IDLE: LiveMeter = {
  running: false,
  startEpochMs: 0,
  distanceMeters: 0,
  movingMs: 0,
  waitingMs: 0,
  manualWait: false,
  autoWaiting: false,
  fare: null,
  tariffName: '',
  tariffEffective: null,
  gpsOk: false,
  accuracyM: null,
  position: null,
  track: [],
};

/** Bridge between the meter and the UI. */
export const liveMeterStore = new Store<LiveMeter>(IDLE);
/** Id of a trip that just finished and whose summary the UI should show. */
export const finishedTripStore = new Store<number | null>(null);

export const METER_TASK = 'namma-meter-location';
const TICK_MS = 1000;
const PERSIST_EVERY_MS = 5000;
const LOCATION_INTERVAL_MS = 1000;
const GPS_STALE_MS = 10_000;
const MAX_TRACK_POINTS = 20_000;

interface ActiveTrip {
  startEpochMs: number;
  config: FareConfig;
  tariffName: string;
  tariffEffective: string | null;
  accumulator: TripAccumulator;
  track: LatLng[];
  lastPersistMs: number;
}

let trip: ActiveTrip | null = null;
let ticker: ReturnType<typeof setInterval> | null = null;
let starting = false;
let stopping = false;

// Must be defined at module scope (imported from the app entry), before any component mounts.
TaskManager.defineTask<{ locations: Location.LocationObject[] }>(METER_TASK, async ({ data, error }) => {
  if (error || !data) return;
  if (!trip) {
    // A stray update with no trip in this process (e.g. after a kill): never run a meter in the background.
    if (!starting) await stopLocationUpdates();
    return;
  }
  onLocations(trip, data.locations);
});

function onLocations(active: ActiveTrip, locations: Location.LocationObject[]): void {
  for (const location of locations) {
    const { latitude, longitude, accuracy, speed } = location.coords;
    const r = active.accumulator.onFix({
      lat: latitude,
      lng: longitude,
      accuracyM: accuracy ?? Number.POSITIVE_INFINITY,
      timeMs: location.timestamp,
      speedMps: speed != null && speed >= 0 ? speed : null,
    });
    if (r.addedMeters > 0 && r.point && active.track.length < MAX_TRACK_POINTS) active.track.push(r.point);
  }
  // Timers can be throttled while the screen is off; location updates keep the clock moving.
  step(active);
}

function fareFor(active: ActiveTrip, end: Date): FareBreakdown {
  return FareCalculator.calculateForTimes(
    active.config,
    active.accumulator.distanceMeters,
    Math.floor(active.accumulator.waitingMs / 1000),
    new Date(active.startEpochMs),
    end,
  );
}

function publish(active: ActiveTrip): void {
  const acc = active.accumulator;
  const now = Date.now();
  liveMeterStore.set({
    running: true,
    startEpochMs: active.startEpochMs,
    distanceMeters: acc.distanceMeters,
    movingMs: acc.movingMs,
    waitingMs: acc.waitingMs,
    manualWait: acc.manualWait,
    autoWaiting: !acc.manualWait && acc.isAutoWaiting(now),
    fare: fareFor(active, new Date(now)),
    tariffName: active.tariffName,
    tariffEffective: active.tariffEffective,
    gpsOk: acc.lastFixMs != null && now - acc.lastFixMs < GPS_STALE_MS,
    accuracyM: acc.lastAccuracyM,
    position: acc.lastPoint,
    track: active.track.slice(),
  });
}

function step(active: ActiveTrip): void {
  if (stopping) return;
  const now = Date.now();
  active.accumulator.tick(now);
  publish(active);
  if (now - active.lastPersistMs >= PERSIST_EVERY_MS) {
    active.lastPersistMs = now;
    persist(active).catch(() => undefined);
  }
}

function persist(active: ActiveTrip): Promise<void> {
  const acc = active.accumulator;
  return LiveTripStore.save({
    startEpochMs: active.startEpochMs,
    distanceMeters: acc.distanceMeters,
    movingMs: acc.movingMs,
    waitingMs: acc.waitingMs,
    manualWait: acc.manualWait,
    tariffName: active.tariffName,
    tariffEffective: active.tariffEffective,
    fareConfig: active.config,
    savedAtEpochMs: Date.now(),
  });
}

async function stopLocationUpdates(): Promise<void> {
  try {
    if (await Location.hasStartedLocationUpdatesAsync(METER_TASK)) await Location.stopLocationUpdatesAsync(METER_TASK);
  } catch {
    // Already stopped.
  }
}

function stopEverything(): void {
  trip = null;
  if (ticker) clearInterval(ticker);
  ticker = null;
  stopLocationUpdates().catch(() => undefined);
}

async function begin(resume: boolean): Promise<void> {
  if (trip || starting) return;
  starting = true;
  stopping = false;
  try {
    const settings = await loadSettings();
    const snapshot = resume ? await LiveTripStore.load() : null;
    const now = Date.now();
    const preset = activePreset(settings);
    const active: ActiveTrip = snapshot
      ? {
          startEpochMs: snapshot.startEpochMs,
          config: snapshot.fareConfig,
          tariffName: snapshot.tariffName,
          tariffEffective: snapshot.tariffEffective,
          accumulator: new TripAccumulator(settings.meter, now, snapshot),
          track: [],
          lastPersistMs: now,
        }
      : {
          startEpochMs: now,
          config: activeFareConfig(settings),
          tariffName: preset?.name ?? t('tariff_custom'),
          tariffEffective: preset?.effectiveDate ?? null,
          accumulator: new TripAccumulator(settings.meter, now),
          track: [],
          lastPersistMs: 0,
        };
    trip = active;
    try {
      await Location.startLocationUpdatesAsync(METER_TASK, {
        accuracy: Location.Accuracy.BestForNavigation,
        timeInterval: LOCATION_INTERVAL_MS,
        distanceInterval: 0,
        pausesUpdatesAutomatically: false,
        activityType: Location.ActivityType.AutomotiveNavigation,
        showsBackgroundLocationIndicator: true,
        foregroundService: {
          notificationTitle: t('app_name'),
          notificationBody: t('notif_channel'),
          notificationColor: '#FFC400',
          // Never keep metering after the app is killed: a killed trip is resumed from the app.
          killServiceOnDestroy: true,
        },
      });
    } catch {
      liveMeterStore.set(IDLE);
      stopEverything();
      return;
    }
    step(active);
    ticker = setInterval(() => trip && step(trip), TICK_MS);
  } finally {
    starting = false;
  }
}

/** Must be called while the app is visible. */
export const MeterService = {
  start: () => begin(false),
  resume: () => begin(true),

  toggleWait(): void {
    const active = trip;
    if (!active) return;
    active.accumulator.tick(Date.now());
    active.accumulator.manualWait = !active.accumulator.manualWait;
    publish(active);
  },

  async stop(): Promise<void> {
    const active = trip;
    if (!active) {
      await LiveTripStore.clear().catch(() => undefined);
      stopEverything();
      return;
    }
    if (stopping) return;
    stopping = true;
    if (ticker) clearInterval(ticker);
    ticker = null;
    await stopLocationUpdates();
    const end = Date.now();
    const acc = active.accumulator;
    acc.tick(end);
    const fare = fareFor(active, new Date(end));
    // A failed save must not crash the app and must not leave a trip that resumes into the same failure.
    let id: number | null = null;
    try {
      id = await TripRepository.save({
        startEpochMs: active.startEpochMs,
        endEpochMs: end,
        distanceMeters: acc.distanceMeters,
        movingSeconds: Math.floor(acc.movingMs / 1000),
        waitingSeconds: Math.floor(acc.waitingMs / 1000),
        tariffName: active.tariffName,
        tariffEffective: active.tariffEffective,
        fare,
      });
    } catch {
      id = null;
    }
    await LiveTripStore.clear().catch(() => undefined);
    stopEverything();
    stopping = false;
    liveMeterStore.set(IDLE);
    if (id != null) finishedTripStore.set(id);
  },

  consumeFinished: () => finishedTripStore.set(null),
};
