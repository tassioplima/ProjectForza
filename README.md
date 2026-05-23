# Forza Gallery

An Android companion app for [forza.net/myforza](https://forza.net/myforza) that lets you browse, download and share your **Forza Motorsport / Forza Horizon** in-game screenshots directly from your phone — with correct orientation, pinch-to-zoom, multi-select batch actions and tablet support.

---

## Why this app exists

The official Forza website has no native mobile download. Browsing on a phone is clunky, photos open in a new tab, and saving them individually is a pain. Orientation is often wrong because the EXIF data embedded by the game is inconsistent.

**Forza Gallery solves all of this.**

---

## Features

### 🔐 Authentication
- Microsoft OAuth login via WebView — session cookies persist across app launches
- Auto-detects when the session expires and redirects back to login
- Bearer token captured automatically from the OAuth flow for native API calls

### 🖼️ Native Gallery
- Fetches your photos directly from the Forza API (`api.forza.net`)
- **Staggered grid** layout — each photo keeps its natural aspect ratio (portrait or landscape)
- **Responsive columns**: 2 on phones · 3 on 7-inch tablets · 4 on 10-inch tablets
- Pull-to-refresh to reload the latest photos
- Empty / error / session-expired states with one-tap recovery

### 📸 Full-Screen Viewer
- Opens any photo in a full-screen immersive viewer with edge-to-edge display
- **Pinch-to-zoom** (1× – 4×) and **pan** with boundary clamping
- **Double-tap** toggles between fit-to-screen and 2× zoom
- **Rotate 90°** clockwise (each tap accumulates — 0 / 90 / 180 / 270°)
- **Download** with EXIF orientation fix (manual rotation applied at save time)
- **Share** — rotation applied before sharing to WhatsApp, Telegram, Instagram, etc.

### ✅ Multi-Select & Batch Actions
- Tap the **Select** toolbar button or **long-press any photo** to enter select mode
- Select up to **10 photos** at once (dimmed when limit is reached)
- **Select All** menu item caps at 10
- **Batch Download** — sequential downloads with live progress counter
- **Batch Share** — single `ACTION_SEND_MULTIPLE` intent with all selected photos
- Exit select mode with the **✕** navigation button or hardware Back

### 🕓 Photo History Badges
- A small badge pill appears on each photo thumbnail after it has been **downloaded** or **shared**
- Shows a ⬇ icon (downloaded), a share icon (shared), or both simultaneously
- Persists across app restarts via `SharedPreferences`
- Helps you avoid accidentally re-downloading or re-sharing the same photo

### 📱 Tablet & Responsive Layout
- Dynamic grid column count via `values-sw600dp` / `values-sw840dp` resources
- Action buttons in the photo viewer use `wrap_content` so they stay compact on wide screens
- `<supports-screens>` declaration for full large/xlarge form-factor compatibility

### 🎨 Design
- **Material Design 3** — tonal palette, elevation, dark/light theme
- Adaptive launcher icon (car silhouette)

---

## Screenshots

> _Add screenshots here after first launch._

---

## Requirements

- **Android 8.0 (API 26)** or higher
- A Forza account linked to a Microsoft account
- USB debugging enabled (for ADB install) **or** sideload the APK directly

---

## Installation

### Option A — Install pre-built APK (easiest)

1. Download `app-debug.apk` from [Releases](https://github.com/tassioplima/ProjectForza/releases)
2. Enable *Install from unknown sources* on your phone
3. Open the APK and tap **Install**

### Option B — Install via ADB script (Windows)

Connect your phone via USB with **USB Debugging** enabled, then run:

```powershell
.\install.bat
```

The script locates the SDK, builds the APK, and installs it in one step.

### Option C — Build in Android Studio

1. Clone the repo
2. Open the project in Android Studio Hedgehog or newer
3. Run on your device (`Shift + F10`)

---

## Building from source

### Prerequisites

| Tool | Version tested |
|---|---|
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17 (JBR bundled with Android Studio) |
| Android SDK | API 34 (Build-Tools 34.0.0) |
| Gradle | 8.6 (wrapper included) |

### Build

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

```bash
# macOS / Linux
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Run unit tests

```powershell
.\gradlew.bat testDebugUnitTest
```

---

## Project structure

```
ProjetoForza/
├── app/src/main/
│   ├── java/com/forzagallery/
│   │   ├── MainActivity.kt            # Login screen — starts OAuth or gallery
│   │   ├── LoginWebViewActivity.kt    # Microsoft OAuth WebView; captures Bearer token
│   │   ├── GalleryActivity.kt         # Native photo grid with select mode & batch bar
│   │   ├── PhotoViewActivity.kt       # Full-screen viewer (zoom · rotate · download · share)
│   │   ├── TouchImageView.kt          # Custom ImageView — pinch-zoom, pan, double-tap, rotation
│   │   ├── PhotoAdapter.kt            # RecyclerView adapter — normal & select modes
│   │   ├── PhotoModel.kt              # Photo data class (id, thumbnailUrl, fullUrl)
│   │   ├── ForzaApiService.kt         # Forza API client (Bearer token · gallery endpoint)
│   │   ├── PhotoHistoryStore.kt       # Persists downloaded/shared photo IDs
│   │   ├── DownloadHelper.kt          # Fetch → EXIF fix → rotate → save to MediaStore
│   │   ├── ShareHelper.kt             # Rotate → cache → FileProvider → share sheet
│   │   └── BatchHelper.kt             # Sequential batch download & multi-share
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml
│       │   ├── activity_gallery.xml   # CoordinatorLayout + batch bar
│       │   ├── activity_photo_view.xml
│       │   └── item_photo.xml         # Card: image · selection overlay · status badges
│       ├── values/                    # M3 colours, strings, integers (2 cols), dimens
│       ├── values-sw600dp/            # integers: gallery_columns = 3
│       ├── values-sw840dp/            # integers: gallery_columns = 4
│       ├── drawable/                  # Vector icons
│       └── xml/file_paths.xml
├── app/src/test/java/com/forzagallery/
│   ├── UrlValidationTest.kt           # URL security validation rules
│   ├── FileNameTest.kt                # Download filename extraction
│   ├── PhotoHistoryTest.kt            # History badge tracking logic (14 tests)
│   └── PhotoAdapterSelectionTest.kt   # Multi-select logic (18 tests)
├── .github/workflows/ci.yml           # GitHub Actions — tests on every push
├── install.bat                        # One-click ADB build + install (Windows)
└── gradlew.bat
```

---

## Architecture

```
MainActivity
    │
    └─► LoginWebViewActivity (Microsoft OAuth)
              │  Bearer token captured from WebView JS
              ▼
        GalleryActivity  ◄──  ForzaApiService (api.forza.net)
              │                     │
              │  tap/long-press      │  PhotoHistoryStore (SharedPreferences)
              ▼                     ▼
        PhotoViewActivity      PhotoAdapter
         (TouchImageView)       (select mode)
              │
    ┌─────────┼──────────┐
    ▼         ▼          ▼
DownloadHelper  ShareHelper  BatchHelper
(MediaStore)  (FileProvider) (sequential)
```

The API layer (`ForzaApiService`) calls `https://api.forza.net/api/v4/me/gallery/FH6` using the Bearer token captured during OAuth. No WebView is involved in the gallery screen.

---

## Security notes

- All network traffic is HTTPS-only (`android:usesCleartextTraffic="false"`)
- Bearer token is stored in-memory only — never written to disk
- `@JavascriptInterface` bridge validates every URL (non-blank, `https://` prefix)
- FileProvider exposes shared files via `content://` URIs — never raw file paths
- `<provider android:exported="false">` — the provider is not accessible to other apps directly

---

## Changelog

| Version | Highlights |
|---|---|
| **v0.6** | Photo history badges (download/share indicators) · unit tests for history + selection |
| **v0.5** | Full-screen photo viewer · pinch-to-zoom · multi-select (up to 10) · batch download/share · long-press select · tablet responsive grid · toolbar icons always visible |
| **v0.4** | Gallery adapts to image orientation (portrait/landscape) via StaggeredGridLayoutManager |
| **v0.3** | Bearer token capture fix · gallery field names corrected |
| **v0.2** | LF line endings enforced · gradlew Unix wrapper added |

---

## Contributing

Pull requests are welcome. Please:

1. Keep code minimal — no ViewModel or DI framework is used intentionally
2. All URL handling must validate input (`https://` prefix enforced)
3. Run `.\gradlew.bat testDebugUnitTest` before opening a PR
4. Add unit tests for any new feature logic

---

## License

MIT — see [LICENSE](LICENSE) for details.


---

## Why this app exists

The official Forza website has no native mobile download. Browsing on a phone is clunky, photos open in a new tab, and the only way to save them is a long-press → save — which often applies the wrong rotation because the EXIF data embedded by the game is inconsistent.

**Forza Gallery solves all of this:**

- Loads `forza.net/myforza` inside a native browser (WebView) with Microsoft account login
- Injects a **Download** button onto every photo card automatically
- Reads EXIF `TAG_ORIENTATION`, rotates the bitmap if needed, **resets EXIF to NORMAL**, and saves to `Pictures/ForzaGallery/` — so the photo looks correct in every gallery app
- Injects a **Share** button with priority routing to WhatsApp, Telegram, Instagram, and other popular apps
- Full **Material Design 3** UI with dark/light theme, tonal palette based on Forza Blue

---

## Features

| Feature | Detail |
|---|---|
| 🔐 Microsoft OAuth login | Handled by the WebView — cookies persist across sessions |
| 🤖 Autofill support | Works with Google Password Manager, Samsung Pass, etc. |
| ⬇️ One-tap download | Photo saved to `Pictures/ForzaGallery/` with gallery notification |
| 🔄 EXIF orientation fix | Rotates bitmap so portrait/landscape is always correct |
| ↗️ Share sheet | Priority apps: WhatsApp, Telegram, Instagram, Snapchat, Twitter/X |
| 🎨 Material Design 3 | Dynamic colour, tonal elevation, dark mode |
| 🔁 Navigation | Back, Home, Refresh, Photos buttons in the toolbar |
| 📐 Adaptive icon | Car silhouette icon (Subaru WRX STI inspired) |

---

## Screenshots

> _Add screenshots here after first launch._

---

## Requirements

- **Android 8.0 (API 26)** or higher
- A **Forza account** linked to a Microsoft account
- USB debugging enabled (for ADB install) **or** install the APK directly

---

## Installation

### Option A — Install pre-built APK (easiest)

1. Download `app-debug.apk` from [Releases](https://github.com/tassioplima/ProjectForza/releases)
2. Enable *Install from unknown sources* on your phone
3. Open the APK and tap **Install**

### Option B — Install via ADB script (Windows)

Connect your phone via USB with **USB Debugging** enabled, then run:

```powershell
.\install.ps1
```

The script checks for ADB, builds the APK (if Gradle is available), and installs it automatically.

### Option C — Build in Android Studio

1. Clone the repo
2. Open the project in Android Studio Hedgehog or newer
3. Run on your device (`Shift + F10`)

---

## Building from source

### Prerequisites

| Tool | Version tested |
|---|---|
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17 or 21 (JBR from Android Studio) |
| Android SDK | API 34 (Build-Tools 34.0.0) |
| Gradle | 8.6 (wrapper included) |

### Build

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```

```bash
# macOS / Linux
export JAVA_HOME="$ANDROID_STUDIO/jbr"
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Project structure

```
ProjetoForza/
├── app/
│   └── src/main/
│       ├── java/com/forzagallery/
│       │   ├── MainActivity.kt          # Single-activity, WebView host + M3 UI
│       │   ├── ForzaJsInterface.kt      # @JavascriptInterface bridge (JS ↔ Android)
│       │   ├── DownloadHelper.kt        # Fetch → EXIF fix → save to MediaStore
│       │   └── ShareHelper.kt          # Cache download → FileProvider → share sheet
│       ├── res/
│       │   ├── drawable/               # Vector icons + adaptive icon layers
│       │   ├── layout/activity_main.xml
│       │   ├── values/                 # M3 colour tokens, strings, themes
│       │   └── xml/file_paths.xml      # FileProvider paths
│       └── AndroidManifest.xml
├── .github/workflows/ci.yml            # GitHub Actions — unit tests on every push
├── install.ps1                         # One-click ADB install script (Windows)
├── gradlew.bat                         # Gradle wrapper (Windows)
└── README.md
```

---

## Architecture

```
WebView (forza.net)
      │  JS injected on every page load
      ▼
ForzaJsInterface  ─── downloadPhoto() ──► DownloadHelper
(JS ↔ Android)    └── sharePhoto()    ──► ShareHelper
                                               │
                                     FileProvider → Intent.ACTION_SEND
```

The app is intentionally simple: **one Activity, no ViewModel, no database**. The WebView handles all the web logic; the Kotlin layer only handles file I/O and system intents.

---

## Security notes

- All network requests are HTTPS-only (`usesCleartextTraffic="false"`)
- The `@JavascriptInterface` bridge validates every URL: must be non-blank and start with `https://`
- FileProvider authority is `com.forzagallery.debug.fileprovider` — files are **never** directly exposed to other apps

---

## Contributing

Pull requests are welcome. Please:

1. Keep the code minimal — this app intentionally has no ViewModel or Dagger
2. All new downloads/shares must validate URLs before processing
3. Run `./gradlew test` before opening a PR

---

## License

MIT — see [LICENSE](LICENSE) for details.
