#!/usr/bin/env bash
# Generates build-info.json with commit hash, date, and build metadata.
# Called during asset copy and CI builds.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

commit_hash=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
commit_full=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
commit_date=$(git log -1 --format=%ci 2>/dev/null || echo "unknown")
commit_msg=$(git log -1 --format=%s 2>/dev/null || echo "unknown")
branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
tag=$(git describe --tags --exact-match 2>/dev/null || echo "none")
dirty=$(git diff --quiet 2>/dev/null && echo "false" || echo "true")
build_date=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
app_count=$(find . -maxdepth 3 -path '*/spec/functionality.md' -print 2>/dev/null | wc -l | tr -d ' ')

jq -n \
    --arg commit "$commit_hash" \
    --arg commit_full "$commit_full" \
    --arg commit_date "$commit_date" \
    --arg commit_msg "$commit_msg" \
    --arg branch "$branch" \
    --arg tag "$tag" \
    --arg dirty "$dirty" \
    --arg build_date "$build_date" \
    --arg app_count "$app_count" \
    '{
        commit: $commit,
        commit_full: $commit_full,
        commit_date: $commit_date,
        commit_message: $commit_msg,
        branch: $branch,
        tag: $tag,
        dirty: ($dirty == "true"),
        build_date: $build_date,
        app_count: ($app_count | tonumber)
    }'
