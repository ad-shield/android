# AdShield Android SDK

Ad-Shield mobile SDK for Android. Detects ad blocking and reports results.

## Installation

Add the dependency to your `build.gradle.kts`:

```gradle
implementation("io.adshield:adshield-android:1.0.0")
```

No additional repositories needed -- the SDK is published to Maven Central.

## Usage

```kotlin
// Application.onCreate() -- just 1 line
AdShield.measure(this)
```

That's it. One line of code.

## How it works

- Detects ad blockers by checking reachability of ad domains
- Sends detection results to Ad-Shield analytics (fire-and-forget)
- Runs in the background, never blocks the main thread
- Runs once per app session (subsequent calls are no-ops)

## Requirements

- Android API 21+ (Android 5.0)
- `INTERNET` permission (declared by the SDK, no action needed)

## API

```kotlin
object AdShield {
    fun measure(context: Context)
}
```

| Parameter | Description |
|-----------|-------------|
| `context` | Application or Activity context. Used to read the package name. |

## Data collected

| Field | Value |
|-------|-------|
| `package` | App package name |
| `platform` | `"android"` |
| `is_adblock_detected` | `true` / `false` |

Server-side enrichment adds: timestamp, IP, country, user agent, device type.

## Publishing (internal)

### Publish to mavenLocal (for testing)

```bash
cd mobile/android
./gradlew :adshield:publishReleasePublicationToMavenLocal
```

### Publish to Maven Central

Set credentials via environment variables or `~/.gradle/gradle.properties`:

```properties
ossrhUsername=your-sonatype-username
ossrhPassword=your-sonatype-password
signingKey=your-gpg-private-key
signingPassword=your-gpg-passphrase
```

Then:

```bash
cd mobile/android
./gradlew :adshield:publishReleasePublicationToSonatypeRepository
```

## License

Proprietary. Copyright Ad-Shield Inc.
