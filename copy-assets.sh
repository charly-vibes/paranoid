#!/usr/bin/env bash
# Copies hub page and mini-app web content into Android assets directory.
# Used by justfile, CI, and release workflows.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ASSETS="android/app/src/main/assets"
rm -rf "$ASSETS"
mkdir -p "$ASSETS"

# Generate build info
bash build-info.sh > build-info.json

# Copy hub page
cp index.html info.html style.css script.js apps-metadata.json build-info.json "$ASSETS/"

# Copy each mini-app directory (excluding spec/ and sessions/)
while IFS= read -r spec; do
    app="$(dirname "$(dirname "$spec")")"
    [ -d "$app" ] || continue
    mkdir -p "$ASSETS/$app"
    find "$app" -maxdepth 1 -type f \( -name '*.html' -o -name '*.css' -o -name '*.js' -o -name '*.json' -o -name '*.png' -o -name '*.jpg' -o -name '*.svg' -o -name '*.woff2' \) -exec cp {} "$ASSETS/$app/" \;
done < <(find . -maxdepth 3 -path '*/spec/functionality.md' -print | sort)

echo "✓ Copied web content to $ASSETS"
