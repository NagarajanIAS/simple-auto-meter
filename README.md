# Namma Meter

A GPS auto-rickshaw fare meter for Tamil Nadu. Native Android (Kotlin, Jetpack Compose, Material 3).

- No server, no login, no ads, no analytics. All data stays on the phone.
- Fares are never hard-coded: every parameter is in `FareConfig`, editable in Settings, with named presets.
- English and Tamil.

> **Reference/estimate only. Not a Legal Metrology-approved fare meter.** Fares follow the selected tariff preset.

## Features

1. **Meter.** START/STOP, manual WAIT, auto-waiting when stopped, live fare/distance/time tiles. Runs in a location foreground service with a notification, so it keeps going with the screen off. The trip state is saved every 5 s and offered for resume if the app is killed. Fare breakdown on STOP, saved to history and shareable as text.
2. **Plan and estimate.** Opens Google Maps for "my location" and driving directions (browser fallback). Day/night fare estimate from km entered by the user. Pick a destination on an in-app OpenStreetMap map (osmdroid) for an *approximate* straight-line × road-factor distance, or a road distance from OpenRouteService with your own free API key.
3. **Driver mode** (Settings, 4-digit PIN). Saves a UPI ID and payee name, shows a full-screen UPI QR with the fare when a trip ends (max brightness, screen kept on), a static QR without an amount, and daily earnings from local history.

## Requirements

- JDK 17 or newer (built and tested with JDK 21)
- Android SDK with platform 36 (`sdk.dir` in `local.properties`, or `ANDROID_HOME`)
- minSdk 26, targetSdk/compileSdk 36

## Architecture

Single module, MVVM, no DI framework (a small `AppContainer` in `NammaMeterApp.kt`).

```
app/src/main/java/com/nammameter/app/
├── fare/        Pure Kotlin, unit-tested
│   ├── FareConfig.kt       every fare parameter (+ NightRule, FareRounding)
│   ├── FarePresets.kt      named tariffs — the only file to touch for a new tariff
│   ├── FareCalculator.kt   BigDecimal fare maths → FareBreakdown
│   ├── NightWindow.kt      night window incl. past-midnight, start/end/any-part rules
│   └── FareEstimator.kt    pre-trip day/night ranges
├── tracking/
│   ├── GpsDistanceFilter.kt  accuracy gate, jump gate, anchor threshold, Kalman smoothing (pure)
│   ├── TripAccumulator.kt    distance + moving/waiting time state machine (pure)
│   ├── MeterService.kt       foreground service: fused location 1 s, wake lock, notification, persistence
│   ├── LiveMeterRepository.kt  StateFlow bridge service → UI
│   └── LocationHelper.kt     permissions + one-shot location
├── data/        Room (trips, money in paise), DataStore (settings, live-trip snapshot)
├── routing/     RoutingProvider: ManualEntry, Haversine, OpenRouteService; Google Maps intents
├── upi/         UPI URI builder + VPA validation, PIN hashing (PBKDF2), ZXing QR
└── ui/          Compose screens, navigation, theme (yellow/black high contrast, dark mode)
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
- Night: if `NightWindow.applies` (default: the trip **starts** in 22:00–05:00), the surcharge is `(minFare + distance charge) × (nightMultiplier − 1)`, plus waiting if `nightAppliesToWaiting`.
- Total is rounded to the nearest rupee (half up) or up to the next rupee.

Example (default **TN 2026** preset: ₹50 for 1.8 km, ₹25/km, ₹1/min waiting, night ×1.5 from 22:00 to 05:00), 5.0 km + 10 min waiting: day `50 + 32×2.50 + 10×1 = 140 → ₹140`; night `(50 + 80)×1.5 + 10 = 205 → ₹205`.

The older TN 2013 preset (₹25 / 1.8 km, ₹12/km, ₹3.50 per 5 min) is kept for reference: 70.40 → ₹70 by day, 102.10 → ₹102 by night.

The fare config is frozen when a trip starts, so changing Settings mid-trip doesn't affect it.

## Adding a new fare preset

When a revised tariff is notified, edit **only** `app/src/main/java/com/nammameter/app/fare/FarePresets.kt`:

```kotlin
val TN_2030 = FarePreset(
    id = "tn_2030",                       // stable; stored in settings
    name = "TN 2030 notified tariff",
    effectiveDate = LocalDate.of(2030, 1, 1),
    source = "G.O. (Ms) No. … dated …",
    config = TN_2026.config.copy(minFare = 60.0, perKmRate = 30.0),
)

val all: List<FarePreset> = listOf(TN_2030, TN_2026, TN_2026_GHAT, TN_2013)
val default: FarePreset = TN_2030
```

Add a test case for the new numbers to `FareCalculatorTest`. Users already on the old preset stay on it until they choose the new one in Settings.

> **Presets included:** TN 2026 (default; notified in the Gazette on 27 Oct 2026 under S.67 of the MV Act, replacing the 2013/2014 notifications), TN 2026 ghat roads (₹60 minimum, ₹30/km), and TN 2013 (superseded). The 2026 figures come from news reports of the notification. Check them against the Gazette text before release, in particular whether waiting is charged per minute and whether the night ×1.5 also applies to waiting (currently off).

## Running the tests

```bash
./gradlew testDebugUnitTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`. 51 JVM tests cover `FareCalculator` (including the TN 2026 tariff in `Tn2026TariffTest`; all required cases: 5 km + 10 min day/night, below/exactly minimum distance, partial steps, waiting blocks, rounding, night boundary crossing under each rule), `NightWindow`, `GpsDistanceFilter` (drift, jumps, re-seeding, accuracy thresholds, a simulated noisy 2 km drive within ±3 %), `TripAccumulator` (auto/manual waiting, resume), routing providers, Google Maps URLs, and UPI URIs/VPA validation/PIN hashing.

Lint: `./gradlew lintDebug`.

## Build and release

### 1. Create an upload keystore (once)

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/namma-meter-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Back up the `.jks` file and both passwords somewhere safe. If you lose them you can't update the app for direct-download users, and for Play you'll need an upload-key reset.

### 2. Configure signing

```bash
cp keystore.properties.example keystore.properties
```

Then edit `keystore.properties`:

```properties
storeFile=keystore/namma-meter-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

`keystore.properties`, `*.jks` and `*.keystore` are git-ignored. `app/build.gradle.kts` reads this file. Without it, release builds come out unsigned.

### 3. Signed APK for direct distribution

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (R8 minified, resources shrunk). Check the signature:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### 4. Signed AAB for Google Play

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Upload it to Play Console with Play App Signing enabled. The keystore above is your *upload* key.

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.

### Play policy material

- `docs/privacy-policy.md` / `docs/privacy-policy.html`: host the HTML and use its URL in the listing. Fill in the contact email first.
- `docs/play-console.md`: Data safety answers, foreground-service-location declaration, prominent disclosure text, permission list.

## Map tiles

The in-app map uses osmdroid with a configurable `{z}/{x}/{y}` tile URL (Settings → Maps & routing). The default is `tile.openstreetmap.org`, which allows only light, interactive use under the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/). The app sends an identifying User-Agent, caches tiles in app storage, never bulk-downloads and shows the © OpenStreetMap attribution. For a published app with many users, switch to a tile provider whose terms allow it.

## Known limitations

- GPS distance is an estimate and will differ from an approved meter, especially among tall buildings.
- After a process kill, time while the app was dead isn't counted. The trip resumes from its last saved state.
- The app can't verify UPI payments. The driver must confirm in their bank/UPI app.
