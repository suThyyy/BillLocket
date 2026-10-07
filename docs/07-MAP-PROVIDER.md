# BillLocket Active Map Provider

Status: Google Maps Compose approved as the active renderer by ADR-016 on 2026-10-06. The earlier BL-032 MapLibre/OpenFreeMap implementation is retained only as project history.

## Decision

BillLocket uses **Google Maps SDK for Android** through **Maps Compose 6.1.2** for the current photo map, transaction-detail mini map and manual point picker.

This decision selects the renderer only. Device-location acquisition, consent, EXIF handling, stored coordinates, transaction data and financial behavior remain provider-neutral.

## Why Google Maps is active

- The current branch already implements and physically tests Google Maps-based photo markers, camera movement, dark/light map styling and a draggable transaction carousel.
- Maps Compose integrates directly with the existing Compose navigation and state model.
- Photo-marker generation, deterministic local clustering and transaction selection remain app-owned rather than depending on a provider database.
- Manual point selection and transaction-detail location preview share the same active renderer.
- The provider-neutral domain boundary still permits a future renderer replacement without changing Room financial/location data.

Tradeoffs are explicit:

- Runtime map rendering requires a Google Cloud project with billing enabled and Maps SDK for Android enabled.
- Google Maps requires an API key embedded into the Android manifest at build time.
- Usage, quotas, terms and possible cost are controlled through Google Cloud, not by BillLocket.
- No-key, invalid-key, disabled-API/billing and offline behavior must remain safe degraded states.

## Configuration

The application uses the Secrets Gradle Plugin:

```text
secrets.properties          # ignored; developer/device runtime key
local.defaults.properties   # tracked; MAPS_API_KEY= blank fallback
AndroidManifest.xml         # ${MAPS_API_KEY} placeholder
```

A clean clone does not need a private key to run unit tests or compile debug/release artifacts because `local.defaults.properties` supplies an empty fallback. A blank key does **not** provide working map tiles on a device.

For authorized local runtime testing, create ignored `secrets.properties`:

```properties
MAPS_API_KEY=your_restricted_android_key
```

Never place the real value in:

- `local.defaults.properties`;
- `local.properties`;
- Gradle scripts;
- manifest XML;
- Kotlin/resources;
- documentation, screenshots or logs;
- Git history.

## Key restrictions and billing safety

The runtime key must be restricted in Google Cloud:

1. Application restriction: Android apps.
2. Package name: `com.billlocket.app`.
3. SHA-1 certificate: add only the debug/release certificate fingerprints actually used to install the app.
4. API restriction: Maps SDK for Android.

Debug and release signing certificates have different fingerprints. A debug-restricted key is sufficient for the local course APK; public distribution requires the release certificate restriction and a normal signing process outside Git.

Billing alerts, quotas and project access are managed in Google Cloud. BillLocket does not store payment details or attempt to manage Google billing from the app.

Although Android map API keys are packaged into the APK, correct package/certificate/API restrictions prevent an extracted value from being freely reused. The repository still treats the real key as local configuration and does not commit it.

## Optional feature and failure isolation

Map and location are optional:

- Camera capture, image import, transaction CRUD, calendar, accounts, budgets, statistics, widgets and backup must work without map rendering.
- Missing/invalid key, disabled Maps API/billing, network failure or provider error must not crash startup or block financial data.
- Room remains the source of truth for coordinates and transaction/image metadata.
- Only confirmed, valid per-image locations become map items.
- Local geotagged photo summaries remain available even if the basemap cannot render.
- Retrying the provider must not duplicate markers, reset financial filters or mutate stored coordinates.

Google Maps SDK may manage its own transient map cache. BillLocket does not scrape, redistribute or implement a custom Google basemap tile cache, and it does not promise offline basemap availability.

## Consent and location ownership

- Location is opt-in in the transaction-image workflow and is never implied by camera or map access.
- Before automatic capture location is enabled, the app asks explicitly and then requests Android permission contextually.
- Denial, timeout or disabled providers never block capture/save.
- Imported images may expose EXIF GPS only as a candidate for confirmation and never receive current device location automatically.
- Manual map selection is initiated explicitly by the user.
- Coordinates belong to each transaction-image row.
- Removing location removes the image from the map without deleting the image or transaction.
- Normalized app-owned media remains stripped of EXIF GPS/device metadata.

Google's optional “my location” presentation does not replace BillLocket's consent/location port and must not silently persist a coordinate.

## Provider-neutral boundary

Domain/data contracts use BillLocket models:

```kotlin
data class GeoPoint(val latitude: Double, val longitude: Double)

data class MapPhoto(
    val imageId: TransactionImageId,
    val transactionId: TransactionId,
    val point: GeoPoint,
    val mediaReference: String,
    val preview: TransactionPreview,
)
```

Google-specific types such as `LatLng`, `CameraPositionState`, `MarkerState`, `MapProperties` and `MapUiSettings` must not cross into Room entities, domain models, repository interfaces or use cases.

```text
Room / TransactionRepository.observeMapPhotos
                    |
      MapPhoto / GeoPoint / MapFilter
                    |
        Map ViewModel state and actions
                    |
       Google Maps Compose adapter
```

The adapter owns:

- conversion from `GeoPoint` to Google `LatLng`;
- camera target and zoom;
- map theme/properties/UI settings;
- marker bitmap descriptors and click handling;
- long-press/manual point selection;
- provider readiness/degraded UI signals.

The repository remains `TransactionRepository.observeMapPhotos(filter)`. A Google-specific repository is not allowed. Place search, geocoding, directions and navigation are not enabled by this decision; each requires a separate API, privacy/cost review and task.

## Current product behavior

- Full-screen Compose map with local month/type/account/category filters.
- Rounded BillLocket transaction-photo markers with selected state and count badge.
- Bounded/cached local bitmap decoding with a stable missing-media fallback.
- Deterministic nearby-photo grouping performed from local data.
- Marker selection synchronizes with the draggable transaction carousel.
- Carousel cards open the matching transaction detail.
- Camera controls can focus the current device location without automatically changing saved image coordinates.
- Long press/manual picker returns a candidate `GeoPoint` for explicit confirmation.
- Transaction detail may show a non-interactive/minimally interactive location preview.
- App theme can select matching Google map styling where implemented.

## Privacy and data handling

- Do not send transaction amount, category, note, image bytes or stable transaction/image IDs as Google Maps service data.
- Marker thumbnails are decoded locally from app-owned media.
- Exact coordinates necessarily reach the map renderer/provider when displayed; this must remain optional and originate only from confirmed local records.
- Do not add Maps analytics beyond unavoidable SDK behavior without a separate decision.
- Google logo, legal notices and attribution must remain visible and unobscured.
- Logs must not contain the API key or exact coordinates.

## Testing and acceptance

### Provider-neutral tests

- Only confirmed valid image locations become map items.
- Filters, cluster ordering and stable IDs are deterministic.
- Missing/corrupt marker media uses the fallback.
- Map interactions never persist coordinates without confirmation.
- Provider failure leaves the local financial core available.

### Build/configuration checks

- A clean clone builds with the blank tracked default and no `secrets.properties`.
- No real key appears in tracked files or `git diff`.
- Debug APK with an authorized restricted key renders tiles on the target device.
- Package/signing restrictions match the installed APK.

### Device checks

- Light/dark/system map theme.
- Photo markers, count badges, selection and carousel synchronization.
- Marker/carousel-to-detail navigation.
- Pan, zoom, recenter and long-press/manual selection.
- Current-location permission accepted/denied/timeout.
- Invalid/blank key and offline behavior do not affect financial screens.
- Google attribution remains visible.

Normal validation remains:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
git diff --check
```

## Migration history

- BL-015 originally selected Google Maps behind a provider-neutral boundary.
- BL-032 replaced it with MapLibre/OpenFreeMap to remove key/billing friction.
- Commit `dac4b51` reintroduced Google Maps with the current carousel, marker and camera experience.
- ADR-016 makes the current source decision explicit and prevents future incidental provider oscillation.

Any later provider change requires a new ADR, dependency/configuration migration, fallback plan and device acceptance. Do not swap renderer as part of unrelated UI work.
