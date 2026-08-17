# Aperture 📡

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

**Aperture** is an Android library that captures and inspects HTTP/HTTPS network traffic through a beautiful web-based interface. Built as a modern alternative to Chucker, Aperture provides real-time monitoring and response mocking capabilities - all accessible from any device on your local network.

## ✨ Features

- 🌐 **Web-Based UI** - Inspect network traffic from any browser on your network
- 📡 **Real-Time Updates** - See requests as they happen with Server-Sent Events (SSE)
- 🎭 **Response Mocking** - Mock API responses without changing your code
- 🗄️ **Persistent Storage** - All requests saved in local database (Room)
- 🔒 **Optional Authentication** - Secure your inspection server with token auth
- 🎨 **Dark Mode** - Automatic dark mode support
- 📱 **Responsive Design** - Works great on mobile and desktop browsers
- 🚀 **Zero Overhead in Release** - No-op implementation for production builds
- 🔍 **Advanced Filtering** - Search by URL, method, or status code
- 📊 **Statistics Dashboard** - View aggregated request metrics
- 🔔 **Foreground Service** - Server runs reliably in background with persistent notification
- ⚡ **Quick Actions** - Stop server or clear data directly from notification

## 🚀 Quick Start

### 1. Add Dependencies

Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation("io.github.desertstack:aperture:1.0.0")
    releaseImplementation("io.github.desertstack:aperture-no-op:1.0.0")

    // Required: Aperture hooks into your OkHttp client and does not bundle it
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

Aperture's own dependencies (Ktor, Room, coroutines, serialization) come in
transitively — you do not need to declare them.

### 1a. Exclude duplicate Netty metadata

Aperture embeds a Ktor/Netty server, which ships metadata files that collide at
packaging time. Add this to the same `build.gradle.kts`, inside `android { }`:

```kotlin
packaging {
    resources {
        excludes += listOf(
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties"
        )
    }
}
```

Without it the build fails with a duplicate-resource error. This cannot be
supplied by the library, since packaging options are application-level.

### 2. Initialize in Application Class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Aperture.initialize(
            context = this,
            config = ApertureConfig(
                enabled = BuildConfig.DEBUG,
                port = 8080,
                autoStart = true
            )
        )
    }
}
```

### 3. Add Interceptor to OkHttp

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(Aperture.getInterceptor())
    .build()
```

### 4. Make HTTP Requests

That's it! Make any HTTP request using your OkHttp client, and Aperture will capture it.

### 5. View in Browser

Check your Logcat for the server URL:

```
Aperture: Server started at: http://192.168.1.100:8080
```


<img src="https://github.com/desertstack/Aperture/blob/main/web_ui.png" width=50% height=50%>

Open this URL in any browser on your network to inspect traffic!

## 🌐 Network Access Options

Aperture provides multiple ways to access the web UI:

### Same Network (WiFi)

When your phone and computer are on the **same WiFi network**:

```
http://192.168.1.100:8080  ← Check Logcat for your device's IP
```

The server binds to `0.0.0.0`, making it accessible from any device on your local network.

### Cellular Data / Different Network (ADB Port Forwarding)

When your phone is on **cellular data** or a **different network**:

1. **Connect device via USB**
2. **Run the ADB forward command** (shown in Logcat):
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
3. **Open in browser**:
   ```
   http://localhost:8080
   ```

The ADB port forwarding tunnels the device's port to your computer's localhost!

### Programmatic Access

```kotlin
// Get network URL (for same WiFi)
val networkUrl = Aperture.getServerUrl()
// Example: http://192.168.1.100:8080

// Get localhost URL (for ADB forwarding)
val localUrl = Aperture.getLocalhostUrl()
// Always: http://localhost:8080

// Get ADB command
val adbCommand = Aperture.getAdbForwardCommand()
// Example: adb forward tcp:8080 tcp:8080
```

## 🔔 Persistent Notification

When `showNotification = true` (default), Aperture runs in a **foreground service** with a persistent notification showing:

- **📡 Request Count** - Total captured requests (updates every 5 seconds)
- **🌐 Network URL** - For same WiFi access
- **🔌 ADB URL** - For port forwarding access
- **💻 ADB Command** - Ready to copy and run
- **Tap to Open** - Opens the network URL in your browser
- **Quick Actions**:
  - **Stop** - Stops the server and service
  - **Clear** - Clears all captured data

**Example notification:**
```
📡 42 requests captured

🌐 Network: http://192.168.1.100:8080
🔌 ADB: http://localhost:8080

💻 Port forward: adb forward tcp:8080 tcp:8080

Tap to open in browser
```

This ensures:
- ✅ Server survives when app goes to background
- ✅ System won't kill the process unexpectedly
- ✅ Easy access to URLs and ADB command
- ✅ Works with cellular data via ADB forwarding
- ✅ Complies with Android foreground service requirements

## 📖 Configuration

### Basic Configuration

```kotlin
Aperture.initialize(
    context = this,
    config = ApertureConfig(
        enabled = true,              // Master enable/disable
        port = 8080,                 // Server port
        autoStart = true,            // Start server automatically
        maxRecords = 1000,           // Max transactions to store
        retentionDays = 7,           // Auto-delete after N days
        maxBodySize = 5 * 1024 * 1024, // 5 MB max body size
        requireAuth = false,         // Require authentication
        showNotification = true,     // Show status notification
        localhostOnly = false,       // Bind to localhost only
        headersToRedact = setOf("Authorization", "Cookie") // Redact sensitive headers
    )
)
```

### Predefined Configurations

```kotlin
// Default configuration
Aperture.initialize(this, ApertureConfig.DEFAULT)

// Minimal (reduced resource usage)
Aperture.initialize(this, ApertureConfig.MINIMAL)

// Localhost only (enhanced security)
Aperture.initialize(this, ApertureConfig.LOCALHOST_ONLY)
```

## 🎭 Response Mocking

### Enable Mocking via Web UI

1. Click on any transaction in the web UI
2. Toggle "Enable Mock Response"
3. Set custom status code, headers, and body
4. Click "Save Mock"
5. Future requests to the same URL will return your mocked response!

### Programmatic Mock Control

```kotlin
// Enable mock for a transaction
Aperture.getRepository()?.setMockEnabled(transactionId, true)

// Update mock response
Aperture.getRepository()?.updateMockResponse(
    id = transactionId,
    responseCode = 200,
    headers = """{"Content-Type": "application/json"}""",
    body = """{"mocked": true}"""
)
```

## 🔧 Runtime Control

```kotlin
// Server control
Aperture.startServer()
Aperture.stopServer()
val isRunning = Aperture.isServerRunning()
val url = Aperture.getServerUrl()

// Data management
Aperture.clearAllData()
Aperture.clearOldData(olderThanDays = 7)

// Get statistics
val count = Aperture.getTransactionCount()
val transaction = Aperture.getTransaction(id)
```

## 🔐 Authentication

Enable token authentication to secure your inspection server:

```kotlin
Aperture.initialize(
    context = this,
    config = ApertureConfig(
        requireAuth = true,
        customToken = "your-secret-token" // Optional, random token generated if not provided
    )
)
```

The auth token will be logged to Logcat:

```
Aperture: Auth token: abc123xyz
```

Include it in API requests:

```
Authorization: Bearer abc123xyz
```

## 🌐 Web UI Features

### Transaction List
- View all HTTP requests in real-time
- Filter by HTTP method (GET, POST, PUT, DELETE)
- Search by URL
- Color-coded status badges (2xx green, 4xx orange, 5xx red)
- Mock indicators for mocked transactions

### Transaction Details
- Complete request/response data
- Pretty-printed JSON
- Headers display
- Timing information
- Error messages

### Mock Management
- Toggle mock on/off
- Edit status codes
- Modify response headers
- Update response bodies
- Live preview

### Statistics Dashboard
- Total requests
- Mocked requests count
- Failed requests count
- Average response time

## 🏗️ Architecture

Aperture is built with modern Android technologies:

- **Kotlin** - 100% Kotlin codebase
- **Room** - Local database for transaction storage
- **OkHttp** - Interceptor for capturing HTTP traffic
- **Ktor** - Embedded web server
- **Kotlin Coroutines** - Async/concurrent operations
- **Kotlinx Serialization** - JSON serialization
- **Server-Sent Events (SSE)** - Real-time updates

## 📱 Requirements

- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 36
- **OkHttp**: 4.x or 5.x
- **Kotlin**: 1.9+

## 🔍 Comparison with Chucker

| Feature | Aperture | Chucker |
|---------|----------|---------|
| Inspection UI | Web browser | In-app |
| Screen size | Any device | Mobile only |
| Real-time updates | ✅ SSE | ✅ |
| Response mocking | ✅ | ❌ |
| Network access | ✅ | ❌ |
| Multi-device viewing | ✅ | ❌ |
| Dark mode | ✅ Auto | ✅ |
| Zero dependencies in release | ✅ | ✅ |

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

```
Copyright 2026 Aperture

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 Acknowledgments

- Inspired by [Chucker](https://github.com/ChuckerTeam/chucker) - excellent in-app HTTP inspector
- Database schema influenced by Chucker's proven design
- Built with ❤️ for the Android community

## 🗺️ Roadmap

### v1.0 (Current)
- ✅ HTTP/HTTPS traffic capture
- ✅ Web-based UI
- ✅ Response mocking
- ✅ Real-time updates
- ✅ Statistics dashboard

### v2.0 (Planned)
- WebSocket inspection
- GraphQL query parsing
- Request replay functionality
- HAR export
- Performance metrics dashboard
- Request filtering by domain/size
- Response delay simulation

## ❓ FAQ

### How do I access the web UI?

Check your Logcat for the server URL. The format is `http://<your-device-ip>:8080`. Open this in any browser on the same network.

### Can I use this in production?

No! Aperture is designed for debug builds only. Use the `debugImplementation` and `releaseImplementation` configuration shown above. The no-op version has zero overhead.

### Does it work with Retrofit?

Yes! Retrofit uses OkHttp under the hood. Just add the interceptor to your OkHttp client and Retrofit will work automatically.

### How do I redact sensitive headers?

Use the `headersToRedact` configuration:

```kotlin
ApertureConfig(
    headersToRedact = setOf("Authorization", "Cookie", "X-Api-Key")
)
```

### What about SSL certificate pinning?

Aperture works normally with SSL pinning since it operates as an OkHttp interceptor, not a proxy.

### Do I need to request notification permission?

For Android 13+ (API 33+), you need to request the `POST_NOTIFICATIONS` permission at runtime to show the foreground service notification. Aperture doesn't handle this automatically - you need to request it in your app:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE
        )
    }
}
```

Alternatively, set `showNotification = false` to run without a foreground service (not recommended for production use).

---

**Made with 📡 by the Aperture team**
