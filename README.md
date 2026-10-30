# Namma Meter

A GPS auto-rickshaw fare meter for Tamil Nadu. React Native (Expo, TypeScript), ported from the original native Kotlin/Jetpack Compose app, which is still on the `main` branch.

- No server, no login, no ads, no analytics. All data stays on the phone.
- Fares are never hard-coded: every parameter is in `FareConfig`, editable in Settings, with named presets.
- English and Tamil.

> **Reference/estimate only. Not a Legal Metrology-approved fare meter.** Fares follow the selected tariff preset.

## Features

1. **Meter.** START/STOP, manual WAIT, auto-waiting when stopped, live fare/distance/time tiles. GPS runs through an expo-location task backed by an Android location foreground service with a notification, so it keeps going with the screen off. The trip state is saved every 5 s and offered for resume if the app is killed. Fare breakdown on STOP, saved to history and shareable as text.
2. **Plan and estimate.** Opens Google Maps for "my location" and driving directions (browser fallback). Day/night fare estimate from km entered by the user. Pick a destination on an in-app OpenStreetMap map (Leaflet in a WebView) for an *approximate* straight-line × road-factor distance, or a road distance from OpenRouteService with your own free API key.
3. **Driver mode** (Settings, 4-digit PIN; while it is on, the tariff settings are also locked behind the PIN). Saves a UPI ID and payee name, shows a full-screen UPI QR with the fare when a trip ends (max brightness, screen kept on), a static QR without an amount, and daily earnings from local history.

## Requirements

- Node.js 20 or newer
- For a device build: JDK 17+ and the Android SDK (`ANDROID_HOME`), or EAS Build in the cloud
- Expo SDK 58 / React Native 0.88. The app uses native modules (location task, SQLite, WebView), so it needs a development build; it does not run in Expo Go.

```bash
npm install
npx expo run:android      # builds and installs a development build, then starts Metro
```

## Architecture

Expo Router for navigation, plain module-level stores (`src/util/Store.ts`, read with `useSyncExternalStore`) instead of ViewModels, no state-management library.

```
index.ts                 entry: defines the location task, then loads expo-router
src/
├── fare/        Pure TypeScript, unit-tested
│   ├── FareConfig.ts       every fare parameter (+ NightRule, FareRounding) and its bounds
│   ├── FarePresets.ts      named tariffs — the only file to touch for a new tariff
│   ├── FareCalculator.ts   exact decimal fare maths (decimal.js) → FareBreakdown
│   ├── NightWindow.ts      night window incl. past-midnight, start/end/any-part rules
│   └── FareEstimator.ts    pre-trip day/night ranges
├── tracking/
│   ├── GpsDistanceFilter.ts  accuracy gate, jump gate, anchor threshold, Kalman smoothing (pure)
│   ├── TripAccumulator.ts    distance + moving/waiting time state machine (pure)
│   ├── MeterService.ts       location task + foreground service, 1 s ticker, persistence, live store
│   └── LocationHelper.ts     permissions + one-shot location
├── data/        expo-sqlite (trips, money in paise, same table as the Room schema), AsyncStorage (settings, live-trip snapshot)
├── routing/     RoutingProvider: ManualEntry, Haversine, OpenRouteService; Google Maps links
├── upi/         UPI URI builder + VPA validation, PIN hashing (PBKDF2), PIN lockout
├── i18n/        en/ta strings generated from the Android strings.xml, printf-style formatter
├── ui/          theme (yellow/black high contrast, dark mode), shared components, Leaflet map page
└── app/         Expo Router screens: meter, summary, history, estimate, map, settings, driver, qr, earnings, about, disclaimer, permission
```

### How the meter measures distance

Raw GPS over-counts distance, especially when stopped. `GpsDistanceFilter`:

1. drops fixes with accuracy worse than 25 m;
2. drops fixes implying more than 90 km/h from the last good fix (and re-seeds after 5 in a row, in case the earlier fix was the outlier);
3. smooths positions with a small Kalman filter weighted by reported accuracy;
4. counts distance from an **anchor** point and moves the anchor only once the segment exceeds `max(5 m, accuracy)`. Stationary drift adds nothing; slow real movement is still counted once it passes the threshold.

Auto-waiting: speed below 5 km/h continuously for more than 60 s. From then on time counts as waiting (the first 60 s count as moving). Manual WAIT overrides it. All thresholds are in Settings → Meter & GPS.

### Fare rules

- Up to `minDistanceKm`: `minFare`.
- Beyond it: `perKmRate × step/1000` per **completed** `distanceStepMeters` step (default 100 m).
- Waiting: `waitingRate` per **completed** `waitingBlockMinutes` block.
- Night: if `nightApplies` (default: the trip **starts** in 22:00–05:00), the surcharge is `(minFare + distance charge) × (nightMultiplier − 1)`, plus waiting if `nightAppliesToWaiting`.
- Total is rounded to the nearest rupee (half up) or up to the next rupee.

Money is computed with `decimal.js` (exact decimal, like `BigDecimal` in the Kotlin app) and stored in integer paise.

Example (default **TN 2026** preset: ₹50 for 1.8 km, ₹25/km, ₹1/min waiting, night ×1.5 from 22:00 to 05:00), 5.0 km + 10 min waiting: day `50 + 32×2.50 + 10×1 = 140 → ₹140`; night `(50 + 80)×1.5 + 10 = 205 → ₹205`.

The older TN 2013 preset (₹25 / 1.8 km, ₹12/km, ₹3.50 per 5 min) is kept for reference: 70.40 → ₹70 by day, 102.10 → ₹102 by night.

The fare config is frozen when a trip starts, so changing Settings mid-trip doesn't affect it.

## Adding a new fare preset

When a revised tariff is notified, edit **only** `src/fare/FarePresets.ts`:

```ts
const TN_2030: FarePreset = {
  id: 'tn_2030', // stable; stored in settings
  name: 'TN 2030 notified tariff',
  effectiveDate: '2030-01-01',
  source: 'G.O. (Ms) No. … dated …',
  config: { ...TN_2026.config, minFare: 60, perKmRate: 30 },
};

const all = [TN_2030, TN_2026, TN_2026_GHAT, TN_2013];
// and set `default: TN_2030`
```

Add a test case for the new numbers to `__tests__/fare/`. Users already on the old preset stay on it until they choose the new one in Settings.

> **Presets included:** TN 2026 (default; notified in the Gazette on 27 Oct 2026 under S.67 of the MV Act, replacing the 2013/2014 notifications), TN 2026 ghat roads (₹60 minimum, ₹30/km), and TN 2013 (superseded). The 2026 figures come from news reports of the notification. Check them against the Gazette text before release, in particular whether waiting is charged per minute and whether the night ×1.5 also applies to waiting (currently off).

## Tests and checks

```bash
npm test             # Jest: fare engine, night window, GPS filter, trip accumulator, routing, UPI, PIN hashing
npm run typecheck    # tsc for the app and the tests
npx expo-doctor
```

The Jest suites are ports of the Kotlin JVM tests (same cases and expected rupee amounts), plus checks that PIN hashes match the Android app's PBKDF2 output and that the string formatter handles the Android placeholders.

## Build and release

Builds use Expo's continuous native generation: `android/` and `ios/` are generated from `app.json` and not committed.

- Local release APK: `npx expo prebuild --platform android`, then build with Gradle in `android/` using your upload keystore (keep `*.jks`/`*.keystore` out of git).
- Cloud: `npx eas-cli@latest build --platform android` (AAB for Google Play; EAS can manage the upload key).

Bump `version` and `android.versionCode` in `app.json` for every release.

Policy material in `docs/` (privacy policy, Play Console answers, security review) was written for the Kotlin build. The data handling is the same; the permission list now comes from `app.json`, which blocks background location and Expo's default storage/overlay/settings permissions.

## Map tiles

The in-app map is Leaflet in a WebView with a configurable `{z}/{x}/{y}` tile URL (Settings → Maps & routing). The default is `tile.openstreetmap.org`, which allows only light, interactive use under the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/). The WebView sends an identifying User-Agent and shows the © OpenStreetMap attribution. Leaflet itself is loaded from unpkg (pinned version with SRI), so the map needs a network connection like the tiles do. For a published app with many users, switch to a tile provider whose terms allow it.

## Differences from the Kotlin app

- The ongoing notification shows a fixed "Running meter" text and has no WAIT action; expo-location's foreground service does not support updating the notification or adding actions. The live fare is in the app.
- Android is the target, as before; iOS has not been built or tested. Background metering on iOS would need the "Always" location permission and background mode, which the app does not request.
- Language is chosen in Settings and stored with the other settings, rather than through Android's per-app language setting.
- Launcher icons are the Expo template placeholders; replace the files in `assets/`.

## Known limitations

- GPS distance is an estimate and will differ from an approved meter, especially among tall buildings.
- After a process kill, time while the app was dead isn't counted. The trip resumes from its last saved state.
- The app can't verify UPI payments. The driver must confirm in their bank/UPI app.
