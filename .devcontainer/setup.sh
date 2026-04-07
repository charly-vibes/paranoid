#!/usr/bin/env bash
set -euo pipefail

echo "Setting up dev environment..."

# Install system tools
sudo apt-get update -qq
sudo apt-get install -y -qq jq shellcheck python3 > /dev/null

# Install just
curl -sSf https://just.systems/install.sh | bash -s -- --to /usr/local/bin 2>/dev/null

# Install Android SDK command-line tools (minimal)
if [ ! -d "${ANDROID_HOME:-.android-sdk}" ]; then
    echo "Installing Android SDK..."
    SDK_DIR="${ANDROID_HOME:-.android-sdk}"
    mkdir -p "$SDK_DIR/cmdline-tools"
    cd /tmp
    curl -sSfO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q commandlinetools-linux-*.zip
    mv cmdline-tools "$SDK_DIR/cmdline-tools/latest"
    rm commandlinetools-linux-*.zip
    yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null
fi

echo "✓ Dev environment ready — run 'just doctor' to verify"
