#!/usr/bin/env bash
# Generates apps-metadata.json from spec and session files.
#
# Each app is emitted as one JSON object on stdout; jq -s slurps the
# stream into a single, well-formed JSON array.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

emit_apps() {
    while IFS= read -r spec; do
        app="$(dirname "$(dirname "$spec")")"
        app="${app#./}"
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

        jq -n \
            --arg name "$app" \
            --arg desc "$desc" \
            --argjson sessions "$sessions" \
            '{name: $name, description: $desc, sessions: $sessions}'
    done < <(find . -maxdepth 3 -path '*/spec/functionality.md' -print | sort)
}

emit_apps | jq -s .
