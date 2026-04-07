# Project Context

## Purpose
A single Android app ("paranoid") that hosts many small apps (mini-apps). Each mini-app is a native Android Activity -- pure Kotlin, minimal dependencies, low resource usage.

## Tech Stack
- Kotlin 1.9+ (Android)
- Android SDK (API 26-35)
- Gradle (Kotlin DSL)
- Pure Android Views (XML layouts) -- no Jetpack Compose
- Constructor injection -- no DI framework (Hilt/Dagger)
- Minimal dependencies per mini-app

## Project Conventions

### Code Style
- Kotlin official code style
- Conventional Commits: `<type>(<scope>): <description>`
- Types: feat, fix, docs, style, refactor, test, chore

### Architecture Patterns
- Hub app (WebView-based) hosts navigation to mini-apps
- Each mini-app is a separate Activity in `android/app/src/main/kotlin/dev/charly/paranoid/apps/<name>/`
- Mini-app specs live in `<name>/spec/functionality.md`
- Shared utilities only if genuinely reused

### Testing Strategy
- TDD: write failing test first, make it pass, then tidy
- Unit tests for data/business logic
- Instrumentation tests for Android components
- Separate commits for refactoring vs behavior changes

### Git Workflow
- Main branch: `main`
- Tidy First: separate tidying from behavior changes, atomic commits
- Conventional Commits

## Domain Context
- Mini-apps are small, self-contained utilities
- Each mini-app should work offline unless the feature requires network
- Dark theme, minimalist design, OLED-friendly
- Resource-conscious: low memory, fast startup

## Important Constraints
- No Jetpack Compose
- No heavy DI frameworks
- Each mini-app uses only what it needs from Android SDK
- APK size conscious

## External Dependencies
- MapLibre Native Android (for map-based mini-apps)
- Google Play Services Location (for GPS)
- Room (for local database)
