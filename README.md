# Aperture 📡

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

**Aperture** is an Android library that inspects a running app from a browser. It started with
HTTP traffic and now reaches the rest of the app's local state: preferences, DataStore,
databases and files. It reads them live, and with one line of config it edits them too.

Everything runs on the device. There is no desktop app, no proxy and no adb wrangling beyond a
single port forward.

## What Aperture inspects

| Panel | Reads | Writes |
|---|---|---|
| **Network** | Every request and response through your OkHttp client, live | Mocks a response for a URL |
| **Preferences** | Every file in `shared_prefs`, with each value's real type | Edit, add and remove keys; clear a file |
| **DataStore** | Preferences DataStore instances you register | Edit, add and remove keys |
| **Databases** | Tables, schema and paged rows in your SQLite databases; a query box | Edit a cell, run a statement |
| **Files** | The app sandbox: files, cache, no_backup and external directories | Edit text, delete, download |

## ✨ Features

- 🌐 **Web console** - the whole app's state from any browser on your network
- 📡 **Live updates** - requests, preference changes and row edits arrive as they happen
- ✍️ **Read and write** - change app state and watch the app pick it up, opt-in per app
- 🎭 **Response mocking** - mock API responses without changing your code
- ⌘ **Command palette and keyboard** - reach any panel, file or table without the mouse
- 🎨 **Light and dark** - follows the system, with a toggle that overrides it
- 📱 **Responsive** - three panes on a laptop, a stack with a tab bar on a phone
- 🔒 **Optional authentication** - token auth, including for the live stream
- 🚀 **Zero overhead in release** - no-op implementation for production builds
- 🛡️ **Safe to drop in** - never crashes your app, never blocks its main thread, never fails a request

## 🚀 Quick Start

### 1. Add Dependencies

Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation("io.github.desertstack:aperture:1.2.0")
    releaseImplementation("io.github.desertstack:aperture-no-op:1.2.0")

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

Put every Aperture call inside one `BuildConfig.DEBUG` check:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Aperture.initialize(
                context = this,
                config = ApertureConfig(
                    port = 8080,
                    autoStart = true
                )
            )
        }
    }
}
```

**Why the check, when the release build is already a no-op.** The release variant's methods are
`inline` and empty, so the calls themselves compile to nothing. Kotlin still evaluates their
*arguments*. Without the guard, `ApertureConfig(...)` is allocated in release, and

```kotlin
Aperture.registerDatabase("app.db", AppDatabase.get(this))   // ← opens the database in release
```

opens a Room database purely to hand it to a method that discards it. One check around the whole
block keeps all of it out.

Call `initialize()` once, from `Application.onCreate()`. The system also runs that method when
your process starts in the background, for a push message, a background job or a widget update.
Aperture handles that case by itself. See
[Background process starts](#background-process-starts).

### 3. Add Interceptor to OkHttp

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(Aperture.getInterceptor())
    .build()
```

`getInterceptor()` never throws. If `initialize()` did not run, or it failed, the call logs and
returns a pass-through interceptor, so your OkHttp client works either way.

### 4. Make HTTP Requests

That's it! Make any HTTP request using your OkHttp client, and Aperture will capture it.

### 5. Reach the rest of your app's state (optional)

Preferences and files need nothing: Aperture finds them and reads them through the same
instances your app is using. Databases and DataStore need one line each.

```kotlin
if (BuildConfig.DEBUG) {
    Aperture.registerDatabase("app.db", appDatabase)          // RoomDatabase or SupportSQLiteDatabase
    Aperture.registerDataStore("settings", settingsDataStore) // DataStore<Preferences>
    Aperture.registerSharedPreferences("secure", encryptedPrefs)
}
```

Why registration is needed, and not merely preferred:

- **Databases.** A second read-write connection makes Android issue `PRAGMA
  journal_mode=PERSIST`, and your database loses WAL permanently. Room's invalidation also runs
  on `CREATE TEMP TRIGGER`, and TEMP objects belong to one connection, so a write from anywhere
  else could never wake your Flows. Registered, Aperture writes through your own connection and
  calls `refreshVersionsAsync()`, so your UI updates while you type in the browser. Unregistered
  databases are still listed and browsed, read-only.
- **DataStore.** `SingleProcessDataStore` keeps a process-wide record of the files it has open
  and throws if a second instance is built over one of them, so Aperture opening its own would
  crash your app. It also caches values in memory and never re-reads the file. Unregistered
  stores are listed by name and size only.
- **EncryptedSharedPreferences.** Read from disk it is ciphertext, because your wrapper holds
  the keys. Register the wrapper and the console shows plain text. Plain preferences files need
  no registration.

All three calls are safe to make after `initialize()`, and none of them throw.

### 6. Turn on writing (optional)

Aperture is read-only until you say otherwise:

```kotlin
ApertureConfig(allowWrites = true)
```

With it off, every route that would change app state answers 403 and the console hides its edit
controls. With it on, edits are still deliberate: a field is read-only until you click Edit, a
save shows the old value beside the new one, reversible writes offer Undo, and a destructive
action names its target on a button labelled with the action.

Statements that would damage your app are refused whatever this setting says: `ATTACH` (which
silently drops your database out of WAL), `VACUUM INTO` (an arbitrary file write), `BEGIN` and
the `PRAGMA`s that reconfigure a database.

### 7. View in Browser

Filter your Logcat on the `Aperture` tag. The server prints where to reach it:

```
Aperture: ═══════════════════════════════════════
Aperture: 🌐 Aperture Server Started
Aperture: ═══════════════════════════════════════
Aperture: 📱 Same Network:  http://192.168.1.100:8080
Aperture: 🔌 ADB Forward:   http://localhost:8080
Aperture:
Aperture: 💻 To access from computer when on cellular:
Aperture:    Run: adb forward tcp:8080 tcp:8080
Aperture:    Open: http://localhost:8080
Aperture: ═══════════════════════════════════════
```

The server binds its port on a background thread, so this banner appears a moment after your
app starts.


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

### What Aperture guarantees

Aperture is a debug tool inside someone else's app, so it holds to three rules:

- **It does not crash the host app.** Every entry point catches its own failures. If
  `initialize()` fails, `getInterceptor()` returns a pass-through interceptor and the app keeps
  its network stack.
- **It does not block the main thread.** Ktor binds its socket on the calling thread, so
  Aperture starts and stops the server on a background thread. The notification reads cached
  values, never the database.
- **It does not fail your requests.** A capture error is logged and dropped. Your request goes
  out once and your response comes back untouched.

### Background process starts

Android 12+ (API 31) refuses a foreground service start while the app is in the background.
`Aperture.initialize()` runs from `Application.onCreate()`, which the system also calls when the
process starts for a push message, a background job or a widget update. Aperture handles the
refusal: it starts the server in the app process, and moves it into the foreground service when
the app shows an activity. The host app does not crash, and the inspector stays available.

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
        headersToRedact = setOf("Authorization", "Cookie"), // Redact sensitive headers
        allowWrites = false,         // Let the console change app state
        inspectors = ApertureInspector.ALL // Which panels to offer
    )
)
```

### Choosing panels

```kotlin
// Traffic only, the way Aperture worked before 1.2
ApertureConfig(inspectors = ApertureInspector.NETWORK_ONLY)

// Everything except the network
ApertureConfig(inspectors = ApertureInspector.STORAGE)

// Exactly what you want
ApertureConfig(inspectors = setOf(ApertureInspector.NETWORK, ApertureInspector.PREFS))
```

A panel you leave out registers no routes at all, so its data never leaves the device.

### What this exposes

Aperture binds every network interface and asks for no token by default. That was captured
traffic; it is now your app's preferences, databases and files. Aperture says so at startup:

```
Aperture: Storage inspection is open at http://192.168.1.100:8080
Aperture: Anyone on this network can read this app's
Aperture: preferences, databases and files.
Aperture: Set requireAuth = true or localhostOnly = true
```

Either setting closes it. The console also shows a warning chip while the port is open.

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

// Registration, for what Aperture cannot reach on its own
Aperture.registerDatabase("app.db", appDatabase)
Aperture.registerDataStore("settings", settingsDataStore)
Aperture.registerSharedPreferences("secure", encryptedPrefs)

// Get statistics
val count = Aperture.getTransactionCount()             // suspending, reads the database
val lastCount = Aperture.getTransactionCountSnapshot() // last known count, safe on any thread
val transaction = Aperture.getTransaction(id)
```

`startServer()` and `stopServer()` return at once and do their work on a background thread,
because Ktor binds and releases its port on the thread that calls it. `isServerRunning()`
therefore turns true shortly after `startServer()` returns, not immediately.

None of these calls throw. Before `initialize()`, or after it failed, they log and do nothing.

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

## 🖥️ The console

A three-pane instrument: an icon rail of panels, a list, and a detail view. Under 820 px the
rail becomes a bottom tab bar and the two panes stack.

### Getting around

| Key | What it does |
|---|---|
| `⌘K` / `Ctrl K` | Command palette: any panel, preferences file, database table or recent request |
| `/` | Focus the list search |
| `j` / `k` | Move down and up the list |
| `g` then a letter | Jump to a panel |
| `Esc` | Close whatever is open |
| `?` | The shortcut list |

Every view has an address. `#/db/notes.db/notes?page=2` survives a reload and can be pasted to
someone else.

### Panels

- **Network** — live list with search, method and status filters that apply together. The detail
  view shows headers, bodies, timing and the mock editor. The list carries summaries only, so a
  captured body never reaches memory for a view that does not draw it.
- **Preferences** and **DataStore** — every key with its real type. An edit keeps that type
  unless you say otherwise, because writing a Long into a key the app reads with `getInt` throws
  inside your app.
- **Databases** — tables, schema, paged rows and a query box. Large cells are cut short inside
  SQLite so a row never exceeds the CursorWindow, with the whole value a click away.
- **Files** — the sandbox as a tree, with text editing, download and delete. The console never
  sends a path: it sends a signed id the device issued.

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
| Network inspection | ✅ | ✅ |
| Preferences, DataStore, databases, files | ✅ | ❌ |
| Editing app state | ✅ | ❌ |
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

## 📋 Changelog

### 1.2.0

Aperture now inspects the rest of the app, not only its traffic, and the console was rebuilt to
carry it.

**Added**

- **Preferences, DataStore, databases and files**, read live and edited with `allowWrites = true`.
- **`Aperture.registerDatabase`, `registerDataStore` and `registerSharedPreferences`.**
  Registration is what lets Aperture write through the app's own connection, so Room's
  invalidation fires and encrypted preferences read as plain text.
- **`ApertureConfig.allowWrites`** (default `false`) and **`ApertureConfig.inspectors`**
  (default every panel).
- **A new console**: three panes, a command palette, keyboard navigation, a density control, and
  a theme toggle over a light and dark palette.
- **A startup warning** when storage inspection is reachable on the network with no token.

**Fixed**

- **Captured content can no longer run as markup.** Every view built itself by pasting URLs,
  headers and response bodies into `innerHTML`. The console now sets text, never markup.
- **The status filter works.** The console sent `4xx` and the server read it with
  `toIntOrNull()`, which is null, so the filter silently did nothing.
- **Filters combine.** Choosing a method used to discard the search text, on both sides.
- **Authentication works with the console.** `requireAuth = true` used to lock the page out of
  its own API, and `EventSource` cannot send a header at all, so the live stream could never
  connect. The console now asks for the token and sends it both ways.
- **The list pages.** It was pinned to the first 100 rows.
- **The console is cached and compressed** instead of being re-read from the APK on every
  request.

**Changed**

- **Network endpoints moved** from `/api/transactions` and `/api/stats` to
  `/api/network/transactions` and `/api/network/stats`, so every panel sits under its own name.
- **Cross-origin requests are limited to loopback origins.** `Access-Control-Allow-Origin: *`
  over captured traffic was already generous; over app databases it is not defensible.
- **Requests to `/api` must arrive by IP address or localhost.** A host name is refused, which
  is what stops a web page from pointing a domain it controls at the device and reading the
  reply.

### 1.1.1

Fixes a crash that reached back to 1.0.0. Upgrade from any earlier version.

**Fixed**

- **A large captured body no longer crashes the app.** Aperture stored bodies of up to 5 MB,
  and base64 grew binary ones by a third on top of that. Android reads a row through a
  CursorWindow of about 2 MB, so one large response made every query that touched it throw
  `SQLiteBlobTooBigException`. A stored body is now capped at 512 KB, and a truncated body says
  so.
- **A database error no longer kills the host app.** The coroutine that watches for new
  transactions ran with no exception handler, so the failure above reached the default handler.
  It now logs and stops watching.
- **The real-time watcher reads one row, not the whole table.** It selected every transaction,
  with every body, on every insert, and then kept only the newest one.
- **A database written by an earlier version is repaired, not abandoned.** `initialize()`
  replaces bodies stored above the ceiling, so transactions captured by 1.0.0 or 1.1.0 become
  readable again. Their metadata, headers and timings survive; only the oversized body goes.
- **Stopping the server releases its database watcher.** A stop and start cycle leaked one
  collector per start.

- **The list endpoint carries no bodies.** `GET /api/transactions` returned every body of
  every listed row, up to 500 of them, held in memory and serialized to JSON. It now answers
  with metadata only, read through a column projection, so the bodies never leave SQLite. The
  live stream carries the same summary.

**Behaviour changes**

- `GET /api/transactions` and the `new_transaction` stream event no longer carry `requestBody`,
  `responseBody`, `requestHeaders`, `responseHeaders`, `mockResponseBody` or
  `mockResponseHeaders`. Read one transaction from `GET /api/transactions/{id}` for those. The
  bundled web UI already did.
- `maxBodySize` above 512 KB has no effect. Android cannot read back a larger row, so the
  ceiling wins. Use the setting to store less, not more.

**Removed**

- `TransactionRepository.getAllAsFlow()`, replaced by `getLatestSummaryAsFlow()`. Unreachable
  through the public API, since `Aperture.getRepository()` is internal.

### 1.1.0

Aperture 1.0.0 could crash the app it was inspecting. This release makes the library safe to
drop into any app. Upgrade from 1.0.0.

**Fixed**

- **The host app no longer crashes on a background process start.** Android 12+ refuses a
  foreground service start while the app is in the background, and threw
  `ForegroundServiceStartNotAllowedException` out of `Application.onCreate()`. Aperture now
  catches the refusal, runs the server in the app process, and moves it into the service when
  the app shows an activity.
- **The main thread does no server work.** Ktor binds and releases its port on the thread that
  calls it. Aperture starts and stops the server on a background thread, so app startup does
  not wait for it.
- **The notification reads no database.** The transaction count and the IP address are cached
  and refreshed on the notification timer thread.
- **The service survives the Android 15 budget.** A `dataSync` foreground service loses its
  daily budget after six hours. Aperture now stops the service on `onTimeout()` and keeps the
  server in the process, instead of leaving the app to an ANR.
- **The service no longer restarts itself into a refused state.** `START_STICKY` became
  `START_NOT_STICKY`.

**Behaviour changes**

- `getInterceptor()` no longer throws `IllegalStateException` before `initialize()`. It logs
  and returns a pass-through interceptor, so your OkHttp client keeps working.
- `startServer()` and `getServerUrl()` degrade the same way. `getServerUrl()` returns an empty
  string when Aperture is not initialized.
- `initialize()` catches its own failures. A broken database no longer takes the app down.
- `isServerRunning()` turns true shortly after `startServer()` returns, because the start now
  happens on a background thread.

**Added**

- `getTransactionCountSnapshot()`, the last known transaction count, with no database read.
  Present in both the full and the no-op artifact.

### 1.0.0

First release.

## 🗺️ Roadmap

### v1.1 (Current)
- ✅ HTTP/HTTPS traffic capture
- ✅ Web-based UI
- ✅ Response mocking
- ✅ Real-time updates
- ✅ Statistics dashboard
- ✅ Safe to initialize from a background process start
- ✅ No server work on the main thread

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

### The notification did not appear. What happened?

Two causes, and neither stops the inspector.

Your process started in the background, so Android refused the foreground service. Aperture
logs `Foreground service refused, starting server in-process` and runs the server anyway. Open
your app once and the notification appears. Until then, reach the web UI over
`adb forward tcp:8080 tcp:8080`.

Or the app has no notification permission on Android 13+. See the next question.

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
