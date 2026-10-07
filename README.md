# BillLocket

BillLocket is a private, offline-first Android money journal built around receipt photos. V1 includes capture/import, transaction CRUD/search, a visual calendar, accounts/categories/transfers, statistics, budgets and alerts, optional per-image location with a Google photo map, a Glance home-screen widget, and portable local backup/restore.

The app lets one device owner record income and expenses with optional photos, review a visual calendar and monthly photo recap, inspect statistics, manage categories, accounts, transfers, and budgets, and use two photo-focused features: a home-screen widget and a map of transaction images with optional per-image locations. Core use is offline-first, requires no account, and remains usable without location permission. See [the V1 requirements](docs/01-REQUIREMENTS.md) for the authoritative scope.

## Prerequisites

- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0

Use the committed Gradle wrapper. No Appwrite account or private backend configuration is required.

## Validation

From PowerShell at the repository root, select an installed JDK 17 and Android SDK (adjust these example paths for your machine):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleRelease
git diff --check
```

JDK 17 runs Gradle; Java/Kotlin bytecode targets remain JVM 11. Android support starts at API 26, with compile/target SDK 35. The wrapper pins Gradle 8.11.1. Dependency downloads require network access on an uncached machine.

`local.properties` is not required when the SDK is supplied through `ANDROID_HOME`. Android Studio may create it for `sdk.dir`; do not commit it or add obsolete Appwrite values.

The optional map uses Google Maps Compose. A clean clone still builds with the tracked blank value in `local.defaults.properties`, but runtime map tiles require a billing-enabled Google Cloud project, Maps SDK for Android, and an Android-restricted `MAPS_API_KEY` supplied through ignored `secrets.properties`. Never commit a real key. Without a valid key or network, local geotagged photo records and all financial features remain usable.

Normal build/test commands do not invoke formatting. Run `.\gradlew.bat spotlessCheck` explicitly for a non-mutating style check, or `.\gradlew.bat spotlessApply` only when intentionally formatting and reviewing the resulting diff. Kotlin formatting is limited to `src/**/*.kt`, excluding generated build output. Existing inherited style is not a build gate.

`.gitattributes` selects LF text, CRLF Windows scripts, and binary assets. Existing untouched files are not mass-rewritten; do not combine repository-wide normalization with feature work.

## Upstream attribution

BillLocket uses the authorized [`luucaohoanq/nocket-compose`](https://github.com/luucaohoanq/nocket-compose) `develop` baseline as its technical starting point. The repository has since removed the inherited backend, authentication, and social product behavior and replaced them with BillLocket's offline-first money-journal architecture.

Files under `Sources/assets/` are retained only as historical upstream references. They are not BillLocket product screenshots or reusable product assets.

## Design documents

- [V1 architecture](docs/03-ARCHITECTURE.md)
- [Room database design](docs/04-DATABASE.md)
- [Business rules](docs/05-BUSINESS-RULES.md)
- [Repository and use-case contracts](docs/06-REPOSITORIES-USE-CASES.md)
- [Google Maps provider decision](docs/07-MAP-PROVIDER.md)
- [Media storage, cleanup and backup](docs/08-MEDIA-STORAGE-BACKUP.md)
- [Navigation and UI-state contracts](docs/09-NAVIGATION-UI-STATE.md)
- [UI/UX specification](docs/12-UIUX-SPEC.md)
- [UI state matrix](docs/14-UI-STATE-MATRIX.md)
- [Current product specification and roadmap](docs/15-CURRENT-PRODUCT-SPEC-AND-ROADMAP.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Future feature ideas](docs/FUTURE_FEATURES.md)
