# Forza Gallery

An Android companion app for [forza.net/myforza](https://forza.net/myforza) that lets you browse, download and share your Forza Motorsport / Forza Horizon in-game photos directly on your phone — with correct orientation, one tap at a time.

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
