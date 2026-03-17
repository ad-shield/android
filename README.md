# AdShield Android SDK

## Installation

Add the dependency to your `build.gradle.kts`:

```gradle
implementation("io.ad-shield:adshield-android:0.0.11")
```

## Usage

```kotlin
// In your Application.onCreate()
AdShield.configure(endpoint = "https://example.ad-shield.io/config") // Contact Ad-Shield to obtain your endpoint
AdShield.measure(this)
```

Contact Ad-Shield to obtain your endpoint URL.

## Sample App

See [android-sample](https://github.com/ad-shield/android-sample) for a working example.

## License

Copyright (c) 2026-present Ad-Shield Inc. All rights reserved.

This software is proprietary and confidential. No part of this software may be reproduced, distributed, modified, reverse-engineered, or used in any form without prior written permission from Ad-Shield Inc.

This software is provided "as is" without warranty of any kind. Ad-Shield Inc. shall not be liable for any damages arising from the use of this software.

Unauthorized use, copying, or distribution of this software is strictly prohibited and may result in legal action.
