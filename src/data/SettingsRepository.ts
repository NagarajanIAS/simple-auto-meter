import AsyncStorage from '@react-native-async-storage/async-storage';
import { defaultGpsFilterParams } from '../tracking/GpsDistanceFilter';
import { Store, useStore } from '../util/Store';
import { AppSettings, defaultDriverSettings, defaultSettings } from './AppSettings';

const KEY = 'settings';

/** Settings persisted as one JSON document; null until loaded. */
export const settingsStore = new Store<AppSettings | null>(null);

let loading: Promise<AppSettings> | null = null;
// Serialises updates so concurrent read-modify-writes don't lose changes.
let queue: Promise<unknown> = Promise.resolve();

/** Missing or unreadable fields fall back to defaults, like the original DataStore mapping. */
function decode(raw: string | null): AppSettings {
  const defaults = defaultSettings();
  if (!raw) return defaults;
  try {
    const p = JSON.parse(raw) as Partial<AppSettings>;
    return {
      ...defaults,
      ...p,
      customFare: { ...defaults.customFare, ...p.customFare },
      meter: { ...defaults.meter, ...p.meter, gps: { ...defaultGpsFilterParams(), ...p.meter?.gps } },
      driver: { ...defaultDriverSettings(), ...p.driver },
    };
  } catch {
    return defaults;
  }
}

export function loadSettings(): Promise<AppSettings> {
  const current = settingsStore.get();
  if (current) return Promise.resolve(current);
  loading ??= AsyncStorage.getItem(KEY).then((raw) => {
    const s = decode(raw);
    settingsStore.set(s);
    return s;
  });
  return loading;
}

export function updateSettings(transform: (s: AppSettings) => AppSettings): Promise<AppSettings> {
  const run = queue.then(async () => {
    const updated = transform(await loadSettings());
    await AsyncStorage.setItem(KEY, JSON.stringify(updated));
    settingsStore.set(updated);
    return updated;
  });
  queue = run.catch(() => undefined);
  return run;
}

export function useSettings(): AppSettings | null {
  return useStore(settingsStore);
}
