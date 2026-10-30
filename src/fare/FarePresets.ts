import { FareConfig } from './FareConfig';

/**
 * A named, government-notified tariff.
 *
 * To add a revised tariff: add one const below and list it in FarePresets.all.
 * Nothing else in the app needs to change.
 */
export interface FarePreset {
  id: string;
  name: string;
  /** ISO date (yyyy-mm-dd) the tariff took effect. */
  effectiveDate: string | null;
  /** Where the numbers come from (G.O. / notification reference). */
  source: string;
  config: FareConfig;
}

const TN_2026: FarePreset = {
  id: 'tn_2026',
  name: 'TN 2026 notified tariff',
  effectiveDate: '2026-10-27',
  source:
    'Govt. of Tamil Nadu Home Dept. notification under S.67 MV Act, 1988 ' +
    '(Gazette 27 Oct 2026; supersedes notifications of 25 Aug 2013 and 16 Oct 2014)',
  config: {
    minFare: 50,
    minDistanceKm: 1.8,
    perKmRate: 25,
    distanceStepMeters: 100,
    waitingRate: 1,
    waitingBlockMinutes: 1,
    nightMultiplier: 1.5,
    nightStartMinutes: 22 * 60,
    nightEndMinutes: 5 * 60,
    nightAppliesToWaiting: false,
    nightRule: 'TRIP_START',
    rounding: 'NEAREST',
  },
};

/** Same notification: ghat roads, minimum ₹60 (1.2 × regular) and ₹30/km. */
const TN_2026_GHAT: FarePreset = {
  id: 'tn_2026_ghat',
  name: 'TN 2026 notified tariff — ghat roads',
  effectiveDate: TN_2026.effectiveDate,
  source: TN_2026.source,
  config: { ...TN_2026.config, minFare: 60, perKmRate: 30 },
};

const TN_2013: FarePreset = {
  id: 'tn_2013',
  name: 'TN 2013 tariff (superseded)',
  effectiveDate: '2013-08-25',
  source: 'Government of Tamil Nadu auto-rickshaw fare revision, 2013',
  config: {
    minFare: 25,
    minDistanceKm: 1.8,
    perKmRate: 12,
    distanceStepMeters: 100,
    waitingRate: 3.5,
    waitingBlockMinutes: 5,
    nightMultiplier: 1.5,
    nightStartMinutes: 22 * 60,
    nightEndMinutes: 5 * 60,
    nightAppliesToWaiting: false,
    nightRule: 'TRIP_START',
    rounding: 'NEAREST',
  },
};

// Example for the next notification:
// const TN_20XX: FarePreset = { id: 'tn_20xx', name: 'TN 20XX notified tariff',
//     effectiveDate: '20XX-MM-DD', source: 'G.O. (Ms) No. …',
//     config: { ...TN_2026.config, minFare: …, perKmRate: … } };

/** Shown in Settings, newest first. Old tariffs stay so past trips and comparisons still make sense. */
const all: readonly FarePreset[] = [TN_2026, TN_2026_GHAT, TN_2013];

export const FarePresets = {
  CUSTOM_ID: 'custom',
  TN_2026,
  TN_2026_GHAT,
  TN_2013,
  all,
  default: TN_2026,
  byId: (id: string): FarePreset | undefined => all.find((p) => p.id === id),
};
