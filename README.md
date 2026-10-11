# AniSteam

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
