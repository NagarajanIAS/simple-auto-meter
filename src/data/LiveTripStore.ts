import AsyncStorage from '@react-native-async-storage/async-storage';
import { FareConfig } from '../fare/FareConfig';
import { Store } from '../util/Store';

/** Live trip state persisted every few seconds so a killed process can resume the trip. */
export interface TripSnapshot {
  startEpochMs: number;
  distanceMeters: number;
  movingMs: number;
  waitingMs: number;
  manualWait: boolean;
  tariffName: string;
  tariffEffective: string | null;
  /** Fare parameters are frozen at trip start. */
  fareConfig: FareConfig;
  savedAtEpochMs: number;
}

const KEY = 'live_trip_snapshot';

/** Last known snapshot, for the resume prompt. undefined until first loaded. */
export const snapshotStore = new Store<TripSnapshot | null | undefined>(undefined);

/** Holds the in-progress trip so it survives a process kill. */
export const LiveTripStore = {
  async load(): Promise<TripSnapshot | null> {
    let snap: TripSnapshot | null = null;
    try {
      const raw = await AsyncStorage.getItem(KEY);
      snap = raw ? (JSON.parse(raw) as TripSnapshot) : null;
    } catch {
      snap = null;
    }
    snapshotStore.set(snap);
    return snap;
  },

  async save(snapshot: TripSnapshot): Promise<void> {
    await AsyncStorage.setItem(KEY, JSON.stringify(snapshot));
    snapshotStore.set(snapshot);
  },

  async clear(): Promise<void> {
    await AsyncStorage.removeItem(KEY);
    snapshotStore.set(null);
  },
};
