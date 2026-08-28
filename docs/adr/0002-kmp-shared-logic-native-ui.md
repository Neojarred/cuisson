# Shared logic in Kotlin Multiplatform, native UI per platform

The domain model, database, import pipeline, parsers and sync live in Kotlin Multiplatform
modules. The interface is Jetpack Compose on Android and will be SwiftUI on iOS. The
expensive and correctness-critical code has nothing to do with pixels, so it is written
once; the interface is the whole reason this app exists, so it is written properly for
each platform.

## Considered options

Android-only native was rejected because iOS is planned and the import pipeline would be
written twice. Compose Multiplatform with a shared interface was rejected because the app
would look like an Android app on iOS, which fights the one requirement that matters most.
Flutter was rejected because both on-device AI integrations and the share sheet would
become platform channel plugins we maintain ourselves, directly under the most
accuracy-critical feature.

## Consequences

Shared code cannot use JVM-only libraries. HTML parsing uses a multiplatform port rather
than jsoup.
