# paranoid

A single Android app hosting many small apps. Each mini-app is a self-contained web page (HTML/CSS/JS) running inside a WebView.

## Quick start

```bash
just new my-app      # scaffold a new mini-app
just serve           # preview in browser at localhost:8000
just build           # build Android APK (requires Android SDK)
just install         # install on connected device
```

## Structure

```
app-name/            # mini-apps at repo root (like jams)
├── index.html
├── style.css
├── script.js
├── spec/functionality.md
└── sessions/

android/             # Android project (WebView shell)
├── app/
└── ...
```
