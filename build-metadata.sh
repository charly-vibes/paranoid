#!/usr/bin/env bash
# Generates apps-metadata.json from spec and session files.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "["
first=true

# Discover apps: directories that have a spec/functionality.md
while IFS= read -r spec; do
    app="$(dirname "$(dirname "$spec")")"
    [ -d "$app" ] || continue

    # Description: first non-empty, non-heading line after ## Purpose
    desc=""
    spec_file="$app/spec/functionality.md"
    if [ -f "$spec_file" ]; then
        desc=$(awk '/^## Purpose/{found=1; next} found && /^#/{exit} found && NF{print; exit}' "$spec_file")
    fi

    # Session files
    sessions="[]"
    if [ -d "$app/sessions" ]; then
        files=$(find "$app/sessions" -name '*.md' -print 2>/dev/null | sort | xargs -I{} basename {} 2>/dev/null || true)
        if [ -n "$files" ]; then
            sessions=$(echo "$files" | jq -R . | jq -s .)
        fi
    fi

    if [ "$first" = true ]; then
        first=false
    else
        echo ","
    fi

    jq -n \
        --arg name "$app" \
        --arg desc "$desc" \
        --argjson sessions "$sessions" \
        '{name: $name, description: $desc, sessions: $sessions}'
done < <(find . -maxdepth 3 -path '*/spec/functionality.md' -print | sort)

echo ""
echo "]"
