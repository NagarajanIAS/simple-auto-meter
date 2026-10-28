# Play Console answers

Answers prepared for the Google Play Console forms. Re-check them against the current form wording before submitting; Google changes the questions from time to time.

## Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No.** All data is processed on the device only. Google's definition of "collect" means transmitting data off the device; Namma Meter does not. |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing collected). The optional OpenRouteService request uses HTTPS. |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no data held by the developer). Users can delete trips in the app or uninstall it. |

Notes for reviewers if asked:

- **Location** is read on the device to measure distance; it is never transmitted by the app.
- **Optional OpenRouteService**: when the user adds their *own* API key and asks for a road-distance estimate, the start and destination coordinates are sent to openrouteservice.org. This is user-initiated and goes to a service the user chose. Under Play's rules, data sent to a third party at the user's explicit request through a feature they set up may count as user-initiated. If you would rather be conservative, declare **Location → Approximate/Precise location → Shared**, purpose **App functionality**, optional, not collected by the developer.
- **Map tiles**: requests to the configured tile server carry only tile coordinates (area viewed) and the IP address, as with any web page.

Other declarations:

- Ads: **No**
- Target audience: 18+ (adult drivers and passengers); not designed for children
- Content rating questionnaire: Utility, no user-generated content, no sharing of location with other users
- Government apps: **No.** The app is not affiliated with any government. Keep the "Not a Legal Metrology-approved fare meter" disclaimer in the listing description.
- Financial features: the app shows a UPI QR code but does not process payments. If asked, answer "No" to providing financial services.

## Foreground service: location (`FOREGROUND_SERVICE_LOCATION`)

**Feature that uses it:** the fare meter. While a trip is running, the app measures distance with GPS so it can show the fare. The trip continues when the driver turns the screen off or switches apps, so the measurement must run in a foreground service.

**Why it can't be deferred or interrupted:** fares depend on the distance and waiting time of the whole trip. Pausing location updates would under- or over-charge the passenger. The task is started by the user (START) and ended by the user (STOP).

**User-visible:** a persistent notification shows the running fare and distance for the whole trip.

**Background location:** not requested. The service is only started from the visible activity, so while-in-use location access is enough.

**Video:** record a 30-second screen capture: open the app → START → show the notification → screen off/on → STOP → fare breakdown. Upload it to YouTube (unlisted) and paste the link.

## Prominent disclosure (location)

The in-app disclosure screen is shown before the system permission dialog. Its text (from `strings.xml`):

> **Namma Meter uses your location to measure the trip**
> To calculate the fare, the app reads your precise GPS location while the meter is running, including when the screen is off or you switch to another app. A notification is shown the whole time.
> • Location is used only on this phone to measure distance and waiting time.
> • It is not uploaded, sold or shared. No account, ads or analytics.
> • Tracking stops when you press STOP.

## Permissions summary

| Permission | Why |
|---|---|
| ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION | Measure trip distance (precise is required for an accurate meter) |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION | Keep the meter running with the screen off |
| POST_NOTIFICATIONS | Show the running fare in the notification |
| WAKE_LOCK | Keep the CPU awake during a trip so GPS fixes are processed |
| INTERNET | Optional OpenRouteService estimates and optional in-app map tiles only |

## Store listing

Privacy policy URL: host `docs/privacy-policy.html` (for example with GitHub Pages) and paste the URL. Fill in the contact email in the policy first.
