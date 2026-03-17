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

Copyright (c) 2024-present Ad-Shield Inc. All rights reserved.

This software is proprietary and confidential. No part of this software may be reproduced, distributed, modified, reverse-engineered, or used in any form without prior written permission from Ad-Shield Inc.

This software is provided "as is" without warranty of any kind. Ad-Shield Inc. shall not be liable for any damages arising from the use of this software.

Unauthorized use, copying, or distribution of this software is strictly prohibited and may result in legal action.
