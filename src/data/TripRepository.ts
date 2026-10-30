import * as SQLite from 'expo-sqlite';
import { useEffect, useState } from 'react';
import { FareBreakdown, paiseToRupees, toPaise } from '../fare/FareCalculator';

/** A completed trip. Money is stored in paise to avoid floating point. */
export interface Trip {
  id: number;
  startEpochMs: number;
  endEpochMs: number;
  distanceMeters: number;
  movingSeconds: number;
  waitingSeconds: number;
  tariffName: string;
  tariffEffective: string | null;
  isNight: boolean;
  chargedExtraKm: number;
  waitingBlocks: number;
  minFarePaise: number;
  extraDistancePaise: number;
  waitingPaise: number;
  nightSurchargePaise: number;
  subtotalPaise: number;
  totalPaise: number;
}

export const tripTotal = (t: Trip) => paiseToRupees(t.totalPaise);

type TripRow = Omit<Trip, 'isNight'> & { isNight: number };
const fromRow = (r: TripRow): Trip => ({ ...r, isNight: r.isNight !== 0 });

// Same table layout as the Android app's Room schema (version 1).
const SCHEMA = `
CREATE TABLE IF NOT EXISTS trips (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  startEpochMs INTEGER NOT NULL,
  endEpochMs INTEGER NOT NULL,
  distanceMeters REAL NOT NULL,
  movingSeconds INTEGER NOT NULL,
  waitingSeconds INTEGER NOT NULL,
  tariffName TEXT NOT NULL,
  tariffEffective TEXT,
  isNight INTEGER NOT NULL,
  chargedExtraKm REAL NOT NULL,
  waitingBlocks INTEGER NOT NULL,
  minFarePaise INTEGER NOT NULL,
  extraDistancePaise INTEGER NOT NULL,
  waitingPaise INTEGER NOT NULL,
  nightSurchargePaise INTEGER NOT NULL,
  subtotalPaise INTEGER NOT NULL,
  totalPaise INTEGER NOT NULL
);`;

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

function db(): Promise<SQLite.SQLiteDatabase> {
  dbPromise ??= SQLite.openDatabaseAsync('namma_meter.db').then(async (d) => {
    await d.execAsync(SCHEMA);
    return d;
  });
  return dbPromise;
}

// Change notifications, so screens re-query like Room's Flow-returning DAO methods.
const listeners = new Set<() => void>();
const notifyChanged = () => listeners.forEach((l) => l());

export interface NewTrip {
  startEpochMs: number;
  endEpochMs: number;
  distanceMeters: number;
  movingSeconds: number;
  waitingSeconds: number;
  tariffName: string;
  tariffEffective: string | null;
  fare: FareBreakdown;
}

export const TripRepository = {
  async save(t: NewTrip): Promise<number> {
    const f = t.fare;
    const result = await (await db()).runAsync(
      `INSERT INTO trips (startEpochMs, endEpochMs, distanceMeters, movingSeconds, waitingSeconds, tariffName,
         tariffEffective, isNight, chargedExtraKm, waitingBlocks, minFarePaise, extraDistancePaise, waitingPaise,
         nightSurchargePaise, subtotalPaise, totalPaise)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      t.startEpochMs,
      t.endEpochMs,
      t.distanceMeters,
      t.movingSeconds,
      t.waitingSeconds,
      t.tariffName,
      t.tariffEffective,
      f.isNight ? 1 : 0,
      f.chargedExtraKm.toNumber(),
      f.waitingBlocks,
      toPaise(f.minFare),
      toPaise(f.extraDistanceCharge),
      toPaise(f.waitingCharge),
      toPaise(f.nightSurcharge),
      toPaise(f.subtotal),
      toPaise(f.total),
    );
    notifyChanged();
    return result.lastInsertRowId;
  },

  async get(id: number): Promise<Trip | null> {
    const row = await (await db()).getFirstAsync<TripRow>('SELECT * FROM trips WHERE id = ?', id);
    return row ? fromRow(row) : null;
  },

  async all(): Promise<Trip[]> {
    const rows = await (await db()).getAllAsync<TripRow>('SELECT * FROM trips ORDER BY startEpochMs DESC');
    return rows.map(fromRow);
  },

  async since(fromEpochMs: number): Promise<Trip[]> {
    const rows = await (await db()).getAllAsync<TripRow>(
      'SELECT * FROM trips WHERE endEpochMs >= ? ORDER BY startEpochMs DESC',
      fromEpochMs,
    );
    return rows.map(fromRow);
  },

  async delete(id: number): Promise<void> {
    await (await db()).runAsync('DELETE FROM trips WHERE id = ?', id);
    notifyChanged();
  },
};

/** Runs [query] now and again after every change to the trips table. undefined while loading. */
export function useTripQuery<T>(query: () => Promise<T>, deps: unknown[]): T | undefined {
  const [value, setValue] = useState<T>();
  useEffect(() => {
    let active = true;
    const run = () => {
      query().then((v) => active && setValue(() => v)).catch(() => undefined);
    };
    run();
    listeners.add(run);
    return () => {
      active = false;
      listeners.delete(run);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return value;
}
