import { FareConfig } from '../fare/FareConfig';
import { FarePreset, FarePresets } from '../fare/FarePresets';
import { RoutingProviderId } from '../routing/RoutingProvider';
import { defaultMeterParams, MeterParams } from '../tracking/TripAccumulator';

export interface DriverSettings {
  enabled: boolean;
  pinHash: string;
  pinSalt: string;
  vpa: string;
  payeeName: string;
  failedPinAttempts: number;
  pinLockedUntilEpochMs: number;
}

export const defaultDriverSettings = (): DriverSettings => ({
  enabled: false,
  pinHash: '',
  pinSalt: '',
  vpa: '',
  payeeName: '',
  failedPinAttempts: 0,
  pinLockedUntilEpochMs: 0,
});

export const hasPin = (d: DriverSettings) => d.pinHash.length > 0;
export const canShowQr = (d: DriverSettings) => d.enabled && d.vpa.length > 0;

/** '' follows the phone's language. */
export type LanguageTag = '' | 'en' | 'ta';

export interface AppSettings {
  presetId: string;
  /** Values used when presetId is FarePresets.CUSTOM_ID. */
  customFare: FareConfig;
  meter: MeterParams;
  roadFactor: number;
  routingProvider: RoutingProviderId;
  orsApiKey: string;
  tileUrl: string;
  driver: DriverSettings;
  disclaimerAccepted: boolean;
  language: LanguageTag;
}

export const DEFAULT_ROAD_FACTOR = 1.35;
export const DEFAULT_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

export const defaultSettings = (): AppSettings => ({
  presetId: FarePresets.default.id,
  customFare: FarePresets.default.config,
  meter: defaultMeterParams(),
  roadFactor: DEFAULT_ROAD_FACTOR,
  routingProvider: 'MANUAL',
  orsApiKey: '',
  tileUrl: DEFAULT_TILE_URL,
  driver: defaultDriverSettings(),
  disclaimerAccepted: false,
  language: '',
});

/** Undefined when the custom tariff is active. */
export const activePreset = (s: AppSettings): FarePreset | undefined => FarePresets.byId(s.presetId);

export const activeFareConfig = (s: AppSettings): FareConfig => activePreset(s)?.config ?? s.customFare;
