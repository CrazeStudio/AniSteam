# AniSteam — Flutter

Flutter implementation for Android, using the existing **AniSteam-2** app as the functional reference.

## Included features
- Dark Material 3 interface with anime catalogue cards
- Search across ToonStream and HiAnime, with source-mode selection
- Anime details and episode lists
- Stream selection, direct-media playback, and external player hand-off
- Local watch history persisted on the device
- Settings for Combined / ToonStream / HiAnime sources
- Loading, empty, network-error, and retry states

## API
The app is configured for `https://anisteam-api-sooty.vercel.app`. Endpoints and source availability depend on the deployed API. The app does not bypass DRM, authentication, paywalls, CAPTCHA, or anti-bot controls.

## Build an APK
1. Open the **Actions** tab.
2. Choose **Build AniSteam APK**.
3. Tap **Run workflow**.
4. Wait for analysis, tests, and the release build.
5. Download `AniSteam-Android-APK` from the successful run's artifacts.

The workflow creates Android platform files on the GitHub runner and uploads the release APK. The APK is available only after a successful workflow run.


Native Android anime application foundation.

## Architecture
- Kotlin + Gradle + Jetpack Compose
- Source-policy gate before third-party integration
- Repository layer for authorized providers
- Media3 for permitted playback sources
- No fake runtime API responses

## Source safety
A provider must be explicitly authorized for the intended automated access and playback integration before it is enabled. The app does not bypass CAPTCHA, DRM, authentication, paywalls, anti-bot controls, or other access restrictions.

## Build
Open with Android Studio and run:
./gradlew assembleDebug
