# AdShield Android SDK

Ad-Shield mobile SDK for Android. Detects ad blocking and reports results.

## Installation

Add the dependency to your `build.gradle.kts`:

```gradle
implementation("io.ad-shield:adshield-android:0.0.2")
```

No additional repositories needed — the SDK is published to Maven Central.

## Usage

```kotlin
// In your Application.onCreate()
AdShield.configure(endpoint = "https://your-endpoint.example.com/config")
AdShield.measure(this)
```

- `configure()` — Sets the config endpoint URL. Contact Ad-Shield (dev@ad-shield.io) to obtain your endpoint.
- `measure()` — Fetches config, detects ad blockers, and reports results. Runs in the background.

## How it works

1. Checks if enough time has passed since the last transmission (`transmissionIntervalMs`)
2. Fetches encrypted config from the configured endpoint
3. Probes ad-related URLs to detect ad blocking (with retries)
4. Sends structured results to the reporting endpoints defined in config
5. All work runs on a background thread — never blocks the main thread

## Requirements

- Android API 21+ (Android 5.0)
- `INTERNET` permission (declared by the SDK, no action needed)

## API

```kotlin
object AdShield {
    fun configure(endpoint: String)
    fun measure(context: Context)
}
```

| Method | Description |
|--------|-------------|
| `configure(endpoint)` | Sets the config endpoint. Must be called before `measure()`. |
| `measure(context)` | Runs detection and reporting. Pass Application or Activity context. |

## Data Collection

This SDK collects limited data for the purpose of ad block detection:

- App package name
- OS version
- Device locale
- Ad block detection results (URL reachability)
- Randomly generated device identifier (UUID, not linked to any personal identity)

**No personally identifiable information (PII) is collected.** The SDK does not access contacts, location, advertising IDs, hardware identifiers, or any other data that could identify an individual.

## License

Proprietary. Copyright Ad-Shield Inc.
