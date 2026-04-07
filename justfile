# paranoid - Android app with mini-apps

docker_image := "paranoid-builder"

# Check prerequisites
doctor:
    #!/usr/bin/env bash
    set -euo pipefail
    ok=true
    check() {
        if command -v "$1" &>/dev/null; then
            printf "  ✓ %-14s %s\n" "$1" "$(command -v "$1")"
        else
            printf "  ✗ %-14s not found — %s\n" "$1" "$2"
            ok=false
        fi
    }
    echo "Checking prerequisites..."
    check just      "https://github.com/casey/just"
    check python3   "https://www.python.org/"
    check jq        "https://jqlang.github.io/jq/"
    check git       "https://git-scm.com/"
    check docker    "https://docs.docker.com/get-docker/"
    echo ""
    echo "Optional:"
    check adb       "comes with Android SDK platform-tools"
    check shellcheck "https://www.shellcheck.net/"
    check typos      "https://github.com/crate-ci/typos"
    check vale       "https://vale.sh/"
    check prek       "https://github.com/j178/prek"
    echo ""
    echo "Docker image:"
    if docker image inspect {{docker_image}} &>/dev/null; then
        printf "  ✓ %-14s ready\n" "{{docker_image}}"
    else
        printf "  ✗ %-14s not built — run 'just docker-build'\n" "{{docker_image}}"
    fi
    echo ""
    if [ "$ok" = true ]; then
        echo "✓ All required tools found"
    else
        echo "✗ Some required tools are missing (see above)"
        exit 1
    fi

# Build the Docker image with Android SDK
docker-build:
    docker build -t {{docker_image}} -f - . <<'DOCKERFILE'
    FROM eclipse-temurin:17-jdk
    RUN apt-get update -qq && apt-get install -y -qq unzip curl && rm -rf /var/lib/apt/lists/*
    ENV ANDROID_HOME=/opt/android-sdk
    RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
        cd /tmp && \
        curl -sSfO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
        unzip -q commandlinetools-linux-*.zip && \
        mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
        rm commandlinetools-linux-*.zip && \
        yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true && \
        $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /dev/null
    WORKDIR /app
    DOCKERFILE
    @echo "✓ Docker image '{{docker_image}}' built"

# Run a gradle command in Docker
_gradle +ARGS:
    docker run --rm -v "$(pwd)":/app -w /app/android {{docker_image}} ./gradlew {{ARGS}}

# Clean build artifacts
clean:
    rm -rf android/app/build android/build
    @echo "✓ Cleaned"

# Create scaffolding for a new mini-app
new APP_NAME:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -d "{{APP_NAME}}" ]; then
        echo "Error: Directory '{{APP_NAME}}' already exists"
        exit 1
    fi
    echo "Creating scaffolding for {{APP_NAME}}..."
    mkdir -p "{{APP_NAME}}/spec" "{{APP_NAME}}/sessions"

    cat > "{{APP_NAME}}/spec/functionality.md" << 'EOF'
    # {{APP_NAME}} - Functionality Specification

    ## Purpose

    [Describe the purpose of this mini-app]

    ## Features

    - [Feature 1]
    - [Feature 2]

    ## Requirements

    - [Requirement 1]
    - [Requirement 2]

    ## Behavior

    [Describe expected behavior]
    EOF

    echo "✓ Created {{APP_NAME}}/spec/functionality.md"
    echo "✓ Created {{APP_NAME}}/sessions/ directory"
    echo ""
    echo "Scaffolding complete! Edit the spec and start building."

# Build debug APK
build:
    just _gradle assembleDebug
    @echo "✓ APK built at android/app/build/outputs/apk/debug/"

# Build release APK (requires signing env vars)
build-release:
    just _gradle assembleRelease
    @echo "✓ Release APK built at android/app/build/outputs/apk/release/"

# Run unit tests
test:
    just _gradle test
    @echo "✓ Tests passed"

# Run lint checks
lint:
    just _gradle lint
    @echo "✓ Lint passed"

# Install on connected device (requires adb)
install: build
    adb install android/app/build/outputs/apk/debug/app-debug.apk
    @echo "✓ Installed on device"

# Start local development server (for GitHub Pages preview)
serve PORT="8000":
    @echo "Starting HTTP server on http://localhost:{{PORT}}"
    @echo "Press Ctrl+C to stop"
    python3 -m http.server {{PORT}}

# Update branch
update:
    @echo "Fetching from origin..."
    git fetch origin
    @echo "Merging origin/main..."
    git merge origin/main
    @echo "✓ Branch updated"
