# AdShield Android SDK

## Installation

Add the dependency to your `build.gradle.kts`:

```gradle
implementation("io.ad-shield:adshield-android:0.0.5")
```

## Usage

```kotlin
// In your Application.onCreate()
AdShield.configure(endpoint = "https://your-endpoint.example.com/config")
AdShield.measure(this)
```

Contact Ad-Shield (dev@ad-shield.io) to obtain your endpoint.

## License

Copyright 2026 Ad-Shield Inc. All rights reserved.

This software is proprietary and confidential. Unauthorized copying, distribution, modification, or use of this software, in whole or in part, is strictly prohibited without prior written permission from Ad-Shield Inc.

This software is provided "as is" without warranty of any kind. Ad-Shield Inc. shall not be liable for any damages arising from the use of this software.
