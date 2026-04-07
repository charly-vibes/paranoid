# paranoid - Android app with mini-apps

# Create scaffolding for a new mini-app
new PAGE_NAME:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -d "{{PAGE_NAME}}" ]; then
        echo "Error: Directory '{{PAGE_NAME}}' already exists"
        exit 1
    fi
    echo "Creating scaffolding for {{PAGE_NAME}}..."
    mkdir -p "{{PAGE_NAME}}/spec" "{{PAGE_NAME}}/sessions"

    cat > "{{PAGE_NAME}}/index.html" << 'EOF'
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <title>{{PAGE_NAME}}</title>
        <link rel="stylesheet" href="style.css">
    </head>
    <body>
        <nav><a href="../index.html" class="home-link">← paranoid</a></nav>
        <main>
            <h1>{{PAGE_NAME}}</h1>
        </main>
        <script src="script.js"></script>
    </body>
    </html>
    EOF

    cat > "{{PAGE_NAME}}/style.css" << 'EOF'
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
    }

    body {
        font-family: system-ui, -apple-system, sans-serif;
        background: #121212;
        color: #e0e0e0;
        line-height: 1.6;
        min-height: 100vh;
        padding: 1rem;
        -webkit-tap-highlight-color: transparent;
    }

    nav {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        padding: 0.75rem 1rem;
        background: #1a1a1a;
        z-index: 100;
    }

    .home-link {
        color: #888;
        text-decoration: none;
        font-size: 0.9rem;
    }

    main {
        max-width: 600px;
        margin: 0 auto;
        padding-top: 3rem;
    }

    h1 {
        font-size: 1.5rem;
        font-weight: 300;
        margin-bottom: 1.5rem;
        color: #fff;
    }
    EOF

    cat > "{{PAGE_NAME}}/script.js" << 'EOF'
    // {{PAGE_NAME}}
    "use strict";

    document.addEventListener('DOMContentLoaded', () => {
        console.log('{{PAGE_NAME}} loaded');
    });
    EOF

    cat > "{{PAGE_NAME}}/spec/functionality.md" << 'EOF'
    # {{PAGE_NAME}} - Functionality Specification

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

    echo "✓ Created {{PAGE_NAME}}/index.html"
    echo "✓ Created {{PAGE_NAME}}/style.css"
    echo "✓ Created {{PAGE_NAME}}/script.js"
    echo "✓ Created {{PAGE_NAME}}/spec/functionality.md"
    echo "✓ Created {{PAGE_NAME}}/sessions/ directory"
    echo ""
    echo "Scaffolding complete! Edit the spec and start building."

# Build apps-metadata.json
metadata:
    @bash build-metadata.sh > apps-metadata.json
    @echo "✓ Built apps-metadata.json"

# Copy web content to Android assets
assets: metadata
    #!/usr/bin/env bash
    set -euo pipefail
    ASSETS="android/app/src/main/assets"
    rm -rf "$ASSETS/apps" "$ASSETS/index.html" "$ASSETS/style.css" "$ASSETS/script.js" "$ASSETS/apps-metadata.json"
    mkdir -p "$ASSETS"
    cp index.html style.css script.js apps-metadata.json "$ASSETS/"
    # Copy each mini-app (folders with spec/functionality.md)
    for app in $(find . -maxdepth 3 -path '*/spec/functionality.md' -printf '%h\n' | sed 's|^\./||;s|/spec$||' | sort); do
        mkdir -p "$ASSETS/$app"
        cp "$app/index.html" "$app/style.css" "$app/script.js" "$ASSETS/$app/" 2>/dev/null || true
    done
    echo "✓ Copied web content to $ASSETS"

# Start local development server (browser)
serve PORT="8000": metadata
    @echo "Starting HTTP server on http://localhost:{{PORT}}"
    @echo "Press Ctrl+C to stop"
    python3 -m http.server {{PORT}}

# Build Android APK (requires Android SDK)
build: assets
    cd android && ./gradlew assembleDebug
    @echo "✓ APK built at android/app/build/outputs/apk/debug/"

# Install on connected device
install: build
    cd android && ./gradlew installDebug
    @echo "✓ Installed on device"

# Update branch
update:
    @echo "Fetching from origin..."
    git fetch origin
    @echo "Merging origin/main..."
    git merge origin/main
    @echo "✓ Branch updated"

# Sync: add all, commit, push
sync MESSAGE="Update":
    @echo "Adding changes..."
    git add .
    @echo "Committing..."
    git commit -m "{{MESSAGE}}"
    @echo "Pushing to origin..."
    git push
    @echo "✓ Changes synced"
