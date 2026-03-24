# AdShield Android SDK

## Installation

Add the dependency to your `build.gradle.kts`:

```gradle
implementation("io.ad-shield:adshield-android:0.0.15")
```

## Usage

```kotlin
// In your Application.onCreate()
AdShield.configure(endpoint = "https://example.ad-shield.io/config") // Contact Ad-Shield to obtain your endpoint
AdShield.measure(this)
```

Contact Ad-Shield to obtain your endpoint URL.

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `endpoint` | `String` | Yes | Configuration endpoint URL provided by Ad-Shield. |
| `kv` | `Map<String, String>` | No | Custom key-value pairs for segmenting report data. Used to break down metrics by custom dimensions (e.g., user type, app section). Defaults to empty. |

#### Example with KV (Kotlin)

```kotlin
AdShield.configure(
    endpoint = "https://example.ad-shield.io/config",
    kv = mapOf("user_type" to "new", "segment" to "premium")
)
AdShield.measure(this)
```

<details>
<summary>Java Usage</summary>

```java
// In your Application.onCreate()
AdShield.configure("https://example.ad-shield.io/config"); // Contact Ad-Shield to obtain your endpoint
AdShield.measure(this);
```

With KV:

```java
Map<String, String> kv = new HashMap<>();
kv.put("user_type", "new");
kv.put("segment", "premium");

AdShield.configure("https://example.ad-shield.io/config", kv);
AdShield.measure(this);
```

</details>

## License

Copyright (c) 2026-present Ad-Shield Inc. All rights reserved.

This software is proprietary and confidential. No part of this software may be reproduced, distributed, modified, reverse-engineered, or used in any form without prior written permission from Ad-Shield Inc.

This software is provided "as is" without warranty of any kind. Ad-Shield Inc. shall not be liable for any damages arising from the use of this software.

Unauthorized use, copying, or distribution of this software is strictly prohibited and may result in legal action.
