# Security review — 28 October 2026

Scope: all app source, manifest, build config and the signed release APK (`versionCode` 1). Method: manual read of every Kotlin file plus an independent second read, `aapt2`/`apksigner` inspection of the release APK, and targeted tests. The app has no backend, so the review centres on what can leave the phone and what someone holding the phone can do.

## Fixed in this review

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | Medium | Unbounded numbers in Settings (`Infinity`, `1e400`, 20-digit values) crashed the app when saved, and a huge minimum fare crashed the service on STOP, losing the trip. | Every fare/meter field now has finite upper bounds enforced in the UI and in `FareConfig`/`MeterParams` validation; the estimator rejects out-of-range input; a failed trip save no longer kills the process or leaves a trip that re-crashes on resume. |
| 2 | Medium-low | Driver PIN could be tried without limit (10,000 combinations). | After 4 wrong attempts the screen locks for 30 s, doubling each time (max ~2 h). The counter is persisted. Verification runs off the main thread. |
| 3 | Low | Map tile URL accepted any string, so a tampered setting could send the driver's position to an arbitrary host. | Must be `https://`, with `{z}`, `{x}` and `{y}`, no whitespace. Cleartext is also now explicitly forbidden by `network_security_config.xml`. |
| 4 | Low | `Upi.payUri` trusted callers to pass a validated VPA. | It now rejects any VPA that fails the format check, so a malformed payee can never add `&am=`/`&pa=` to the QR. |
| 5 | Low | PIN digits were kept in saved instance state. | PIN fields use plain `remember`; nothing is written to the activity bundle. |
| 6 | Low | "Change PIN" deleted the old PIN before the new one was set. | Change is now a single atomic write. |
| 7 | Hardening | OpenRouteService call used `HttpURLConnection`. | Cast to `HttpsURLConnection`, redirects disabled, so the user's API key only ever goes to `api.openrouteservice.org` over TLS. |

## Checked and found clean

- **Exported surface:** only `MainActivity` (launcher, no deep links, no extras read). `MeterService` is not exported; `START_NOT_STICKY`. PendingIntents are immutable with explicit components.
- **Permissions:** fine/coarse location, foreground service (location), notifications, wake lock, internet. No background location. Permission is checked before every location call; `SecurityException` is caught in the service.
- **Network:** one HTTPS endpoint (optional, user's own key, two coordinates in the body) plus map tiles. No cleartext. No analytics/crash SDK. Zero `Log` calls.
- **Storage:** Room and DataStore in app-private storage; backup and device transfer fully excluded; osmdroid cache under `cacheDir`. No external storage.
- **Data in QR / share / Maps intents:** all user strings are percent-encoded; amounts are formatted `BigDecimal`; SQL is parameterised via Room.
- **Release build:** R8 minified (no app class names survive), not debugging, signed with APK Signature Scheme v2, signing material git-ignored.

## Accepted as is

- The OpenRouteService key and the salted PBKDF2 PIN hash sit in plaintext app-private DataStore. Reachable only on a rooted device. Could be wrapped with an Android Keystore key if the threat model grows.
- ~~Tariff values are editable without the PIN.~~ Fixed: while driver mode is on, the tariff section in Settings is read-only until the driver PIN is entered (same lockout as the driver screen).
- A 4-digit PIN protects against casual tampering, not a determined attacker with the unlocked phone; the lockout makes guessing impractical in practice.

Keep `app/build/outputs/mapping/release/mapping.txt` for each release so crash traces can be de-obfuscated.
