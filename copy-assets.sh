#!/usr/bin/env bash
# Copies hub page and mini-app web content into a target assets directory.
# Used by Android packaging (default), CI deploy, and release workflows.
#
# Usage: copy-assets.sh [TARGET_DIR]
#   TARGET_DIR defaults to android/app/src/main/assets

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

TARGET="${1:-android/app/src/main/assets}"
rm -rf "$TARGET"
mkdir -p "$TARGET"

# Generate build info
bash build-info.sh > build-info.json

# Copy hub page (HTML/CSS/JS, metadata, icons, logo)
cp index.html info.html style.css script.js apps-metadata.json build-info.json \
   favicon.png apple-touch-icon.png paranoid_logo_bic_pen.svg "$TARGET/"

# Copy each mini-app directory (excluding spec/ and sessions/)
while IFS= read -r spec; do
    app="$(dirname "$(dirname "$spec")")"
    [ -d "$app" ] || continue
    mkdir -p "$TARGET/$app"
    find "$app" -maxdepth 1 -type f \( -name '*.html' -o -name '*.css' -o -name '*.js' -o -name '*.json' -o -name '*.png' -o -name '*.jpg' -o -name '*.svg' -o -name '*.woff2' \) -exec cp {} "$TARGET/$app/" \;
done < <(find . -maxdepth 3 -path '*/spec/functionality.md' -print | sort)

echo "✓ Copied web content to $TARGET"
