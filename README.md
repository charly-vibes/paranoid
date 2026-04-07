# paranoid

[![tracked with wai](https://img.shields.io/badge/tracked%20with-wai-blue)](https://github.com/charly-vibes/wai)

A single Android app hosting many small apps. Each mini-app is a native Android Activity — pure Kotlin, minimal dependencies, low resource usage.

## Prerequisites

- [just](https://github.com/casey/just) - command runner
- [Python 3](https://www.python.org/) - for local dev server
- [jq](https://jqlang.github.io/jq/) - for metadata generation
- [Android SDK](https://developer.android.com/studio) - for building the APK (optional for web-only dev)

## Dev setup

```bash
git clone <repo-url> && cd paranoid
just doctor                    # check your environment
just serve                     # preview in browser at localhost:8000
```

For Android builds, install the Android SDK (easiest via [Android Studio](https://developer.android.com/studio)) and set `ANDROID_HOME`:

```bash
export ANDROID_HOME=~/Android/Sdk    # adjust to your path
just build                           # build debug APK
just install                         # install on connected device/emulator
```

## Usage

```bash
just new my-app      # scaffold a new mini-app
just serve           # preview hub + all mini-apps in browser
just build           # build Android debug APK
just install         # build + install on connected device
just doctor          # check prerequisites
just                 # list all available commands
```

## Run locally

**In browser** (no Android SDK needed):

```bash
just serve
# open http://localhost:8000
```

This serves the hub page and all mini-apps. Works exactly like the Android app but in your browser.

**On Android device/emulator**:

```bash
just install         # build and install on connected device
# or
just build           # just build the APK
# APK is at android/app/build/outputs/apk/debug/app-debug.apk
```

To run on an emulator, create one in Android Studio's Device Manager first.

## Deploy

**Debug APK** (local testing):
```bash
just build
# share android/app/build/outputs/apk/debug/app-debug.apk
```

**Release APK** (via GitHub Actions):
Push a version tag to trigger a release build:
```bash
git tag v1.0.0
git push origin v1.0.0
# GitHub Actions builds a signed APK and creates a release
```

## Structure

```
my-app/              # mini-apps at repo root (like jams)
├── index.html
├── style.css
├── script.js
├── spec/
│   └── functionality.md
└── sessions/

android/             # Android project (WebView shell)
├── app/
├── gradlew
└── ...

.wai/                # wai project tracking
.beads/              # issue tracking
openspec/            # change proposals
```
