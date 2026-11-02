import { haversineMeters, LatLng } from '../../src/tracking/Geo';
import { defaultGpsFilterParams, GpsDistanceFilter, LocationFix } from '../../src/tracking/GpsDistanceFilter';
import { defaultMeterParams, TripAccumulator } from '../../src/tracking/TripAccumulator';

const origin: LatLng = { lat: 13.0827, lng: 80.2707 }; // Chennai
const METERS_PER_DEG_LAT = 111_195;
const north = (m: number): LatLng => ({ lat: origin.lat + m / METERS_PER_DEG_LAT, lng: origin.lng });

/** Seeded PRNG (mulberry32) so the noisy-GPS tests are repeatable. */
function seeded(seed: number) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return { next, between: (lo: number, hi: number) => lo + (hi - lo) * next() };
}

const fix = (p: LatLng, sec: number, acc = 5, speed: number | null = null): LocationFix => ({
  lat: p.lat,
  lng: p.lng,
  accuracyM: acc,
  timeMs: sec * 1000,
  speedMps: speed,
});

describe('GpsDistanceFilter', () => {
  const raw = () => new GpsDistanceFilter(defaultGpsFilterParams({ smoothing: false }));

  test('haversine matches known distance', () => {
    // Chennai Central → Chennai Egmore is roughly 1.75 km straight line.
    const d = haversineMeters({ lat: 13.0827, lng: 80.275 }, { lat: 13.078, lng: 80.2595 });
    expect(d).toBeGreaterThanOrEqual(1700);
    expect(d).toBeLessThanOrEqual(1800);
    expect(haversineMeters(origin, north(1000))).toBeCloseTo(1000, -0.3);
  });

  test('inaccurate fixes are discarded', () => {
    const f = raw();
    f.process(fix(origin, 0));
    const r = f.process(fix(north(200), 20, 40));
    expect(r.accepted).toBe(false);
    expect(r.rejectReason).toBe('INACCURATE');
  });

  test('impossible jumps are discarded', () => {
    const f = raw();
    f.process(fix(origin, 0));
    // 100 m in 1 s = 360 km/h
    expect(f.process(fix(north(100), 1)).rejectReason).toBe('JUMP');
    // A sane fix afterwards is measured from the last good fix.
    const ok = f.process(fix(north(20), 2));
    expect(ok.accepted).toBe(true);
    expect(Math.abs(ok.addedMeters - 20)).toBeLessThan(0.5);
  });

  test('persistent jumps reseed instead of rejecting forever', () => {
    const f = raw();
    f.process(fix({ lat: origin.lat + 1, lng: origin.lng }, 0)); // bad first fix ~111 km away
    let total = 0;
    for (let i = 1; i <= 10; i++) total += f.process(fix(north(i * 10), i)).addedMeters;
    expect(total).toBeGreaterThanOrEqual(40);
    expect(total).toBeLessThanOrEqual(60);
  });

  test('stationary drift adds no distance', () => {
    const f = new GpsDistanceFilter();
    const rnd = seeded(7);
    let total = 0;
    for (let t = 0; t < 600; t++) {
      // ±4 m jitter with 8–15 m reported accuracy, for 10 minutes.
      const p = {
        lat: origin.lat + ((rnd.next() - 0.5) * 8) / METERS_PER_DEG_LAT,
        lng: origin.lng + ((rnd.next() - 0.5) * 8) / METERS_PER_DEG_LAT,
      };
      total += f.process(fix(p, t, 8 + rnd.next() * 7, 0.3)).addedMeters;
    }
    expect(total).toBeCloseTo(0, 3);
  });

  test('straight drive is measured within 3 percent', () => {
    const f = new GpsDistanceFilter();
    const rnd = seeded(3);
    let total = 0;
    // 2 km at 8.33 m/s (30 km/h), one fix per second, ±3 m noise.
    const seconds = Math.floor(2000 / 8.333);
    for (let t = 0; t <= seconds; t++) {
      const along = t * 8.333;
      const p = {
        lat: origin.lat + (along + (rnd.next() - 0.5) * 6) / METERS_PER_DEG_LAT,
        lng: origin.lng + ((rnd.next() - 0.5) * 6) / METERS_PER_DEG_LAT,
      };
      total += f.process(fix(p, t, 6, 8.3)).addedMeters;
    }
    expect(Math.abs(total - 2000)).toBeLessThanOrEqual(60);
  });

  test('small movements below threshold accumulate once threshold passed', () => {
    const f = new GpsDistanceFilter(defaultGpsFilterParams({ smoothing: false, minSegmentM: 5 }));
    f.process(fix(origin, 0, 3));
    expect(f.process(fix(north(2), 1, 3)).addedMeters).toBe(0);
    expect(f.process(fix(north(4), 2, 3)).addedMeters).toBe(0);
    expect(Math.abs(f.process(fix(north(6), 3, 3)).addedMeters - 6)).toBeLessThan(0.05);
  });

  test('threshold scales with accuracy', () => {
    const f = raw();
    f.process(fix(origin, 0, 20));
    expect(f.process(fix(north(15), 5, 20)).addedMeters).toBe(0);
    expect(Math.abs(f.process(fix(north(25), 10, 20)).addedMeters - 25)).toBeLessThan(0.1);
  });

  test('out of order fixes are rejected', () => {
    const f = new GpsDistanceFilter();
    f.process(fix(origin, 10));
    expect(f.process(fix(north(10), 10)).rejectReason).toBe('OUT_OF_ORDER');
  });
});

describe('TripAccumulator', () => {
  const params = defaultMeterParams({ gps: defaultGpsFilterParams({ smoothing: false }) });
  const kmhFix = (p: LatLng, sec: number, speedKmh: number) => fix(p, sec, 5, speedKmh / 3.6);

  test('stopped for more than 60 s starts accruing waiting', () => {
    const acc = new TripAccumulator(params, 0);
    for (let s = 0; s <= 120; s++) {
      acc.onFix(kmhFix(origin, s, 0));
      acc.tick(s * 1000);
    }
    expect(acc.isAutoWaiting(120_000)).toBe(true);
    // The first 60 s count as moving time, the remainder as waiting.
    expect(acc.movingMs).toBe(60_000);
    expect(acc.waitingMs).toBe(60_000);
  });

  test('moving resets the auto wait timer', () => {
    const acc = new TripAccumulator(params, 0);
    for (let s = 0; s <= 50; s++) {
      acc.onFix(kmhFix(origin, s, 0));
      acc.tick(s * 1000);
    }
    // Drive 100 m at 36 km/h, then stop again for under a minute.
    for (let s = 51; s <= 60; s++) {
      acc.onFix(kmhFix(north((s - 50) * 10), s, 36));
      acc.tick(s * 1000);
    }
    for (let s = 61; s <= 100; s++) {
      acc.onFix(kmhFix(north(100), s, 0));
      acc.tick(s * 1000);
    }
    expect(acc.isAutoWaiting(100_000)).toBe(false);
    expect(acc.waitingMs).toBe(0);
  });

  test('manual wait overrides', () => {
    const acc = new TripAccumulator(params, 0);
    acc.manualWait = true;
    acc.onFix(kmhFix(origin, 0, 40));
    acc.tick(30_000);
    expect(acc.waitingMs).toBe(30_000);
    acc.manualWait = false;
    acc.tick(40_000);
    expect(acc.movingMs).toBe(10_000);
  });

  test('jittery fixes without receiver speed still detect a stop', () => {
    // Emulator / some phones: no speed field, ±2 m jitter every second.
    const acc = new TripAccumulator(defaultMeterParams(), 0);
    const rnd = seeded(1);
    for (let s = 0; s <= 150; s++) {
      const p = { lat: origin.lat + rnd.between(-2, 2) / METERS_PER_DEG_LAT, lng: origin.lng + rnd.between(-2, 2) / METERS_PER_DEG_LAT };
      acc.onFix(fix(p, s, 5, null));
      acc.tick(s * 1000);
    }
    expect(acc.isAutoWaiting(150_000)).toBe(true);
    expect(acc.waitingMs).toBeGreaterThanOrEqual(80_000);
    expect(acc.waitingMs).toBeLessThanOrEqual(90_000);
    expect(acc.distanceMeters).toBeCloseTo(0, 3);
  });

  test('driving without receiver speed is not waiting', () => {
    const acc = new TripAccumulator(defaultMeterParams(), 0);
    for (let s = 0; s <= 120; s++) {
      acc.onFix(fix({ lat: north(s * 8.33).lat, lng: origin.lng }, s, 5, null));
      acc.tick(s * 1000);
    }
    expect(acc.isAutoWaiting(120_000)).toBe(false);
    expect(acc.waitingMs).toBe(0);
  });

  test('receiver speed spikes while parked do not reset the stop timer', () => {
    // Seen on the emulator: fused speed reads up to 1.5 m/s (5.4 km/h) while standing still.
    const acc = new TripAccumulator(params, 0);
    for (let s = 0; s <= 120; s++) {
      acc.onFix(fix(origin, s, 5, s % 7 === 0 ? 1.5 : 0.01));
      acc.tick(s * 1000);
    }
    expect(acc.isAutoWaiting(120_000)).toBe(true);
    expect(acc.waitingMs).toBeGreaterThanOrEqual(50_000);
  });

  test('receiver reporting zero speed while driving is not waiting', () => {
    const acc = new TripAccumulator(params, 0);
    for (let s = 0; s <= 120; s++) {
      acc.onFix(fix({ lat: north(s * 8.33).lat, lng: origin.lng }, s, 5, 0.01));
      acc.tick(s * 1000);
    }
    expect(acc.waitingMs).toBe(0);
  });

  test('restored state continues from snapshot', () => {
    const acc = new TripAccumulator(params, 1_000, { distanceMeters: 1234, movingMs: 5000, waitingMs: 7000 });
    acc.tick(2_000);
    expect(acc.distanceMeters).toBe(1234);
    expect(acc.movingMs).toBe(6000);
    expect(acc.waitingMs).toBe(7000);
  });

  test('distance accumulates from accepted fixes', () => {
    const acc = new TripAccumulator(params, 0);
    for (let s = 0; s <= 10; s++) acc.onFix(kmhFix(north(s * 10), s, 36));
    expect(Math.abs(acc.distanceMeters - 100)).toBeLessThan(0.5);
  });
});
