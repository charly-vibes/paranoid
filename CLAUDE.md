<!-- WAI:START -->
# Workflow Tools

This project uses **wai** to track the *why* behind decisions — research,
reasoning, and design choices that shaped the code. Run `wai status` first
to orient yourself.

## Starting a Session

1. Run `wai status` to see active projects, current phase, and suggestions.
2. Check the phase — it tells you what kind of work is expected:
   - **research** → gather information, explore options
   - **design** → make architectural decisions
   - **plan** → break work into tasks
   - **implement** → write code, guided by research/plans
   - **review** → validate against plans
   - **archive** → wrap up
3. Read existing artifacts with `wai search "<topic>"` before starting new work.

## Capturing Work

Record the reasoning behind your work, not just the output:

```bash
wai add research "findings"         # What you learned, trade-offs
wai add plan "approach"             # How you'll implement, why
wai add design "decisions"          # Architecture choices, rationale
wai add research --file notes.md    # Import longer content
```

Use `--project <name>` if multiple projects exist. Otherwise wai picks the first one.

Phases are a guide, not a gate. Use `wai phase show` / `wai phase next`.

## Ending a Session

Before saying "done", run this checklist:

```
[ ] wai handoff create <project>   # capture context for next session
[ ] wai reflect                    # update CLAUDE.md with project patterns (every ~5 sessions)
[ ] git add <files> && git commit  # commit code + handoff
```

### Autonomous Loop

One task per session. The resume loop:

1. `wai prime` — orient (shows ⚡ RESUMING if mid-task)
2. Work on the single task
3. `wai close` — capture state (run this before every `/clear`)
4. `git add <files> && git commit`
5. `/clear` — fresh context

→ Next session: `wai prime` shows RESUMING with exact next steps.

When context reaches ~40%: run `wai close`, then `/clear`.
Do NOT skip `wai close` — it enables resume detection.

## Quick Reference

### wai
```bash
wai status                    # Project status and next steps
wai add research "notes"      # Add research artifact
wai add plan "plan"           # Add plan artifact
wai add design "design"       # Add design artifact
wai add skill <name>          # Scaffold a new agent skill
wai search "query"            # Search across artifacts
wai search --tag <tag>        # Filter by tag (repeatable)
wai search --latest           # Most recent match only
wai why "why use TOML?"       # Ask why (LLM-powered oracle)
wai why src/config.rs         # Explain a file's history
wai reflect                   # Synthesize project patterns into CLAUDE.md
wai close                     # Session handoff + pending-resume signal
wai phase show                # Current phase
wai doctor                    # Workspace health
wai pipeline list             # List pipelines
wai pipeline run <n> --topic=<t>  # Start a run; set WAI_PIPELINE_RUN=<id>
wai pipeline advance <run-id> # Mark stage done, get next hint
```

## Structure

The `.wai/` directory organizes artifacts using the PARA method:
- **projects/** — active work with phase tracking and dated artifacts
- **areas/** — ongoing responsibilities (no end date)
- **resources/** — reference material, agent configs, templates
- **archives/** — completed or inactive items

Do not edit `.wai/config.toml` directly. Use `wai` commands instead.

Keep this managed block so `wai init` can refresh the instructions.

<!-- WAI:END -->

<!-- WAI:REFLECT:REF:START -->
## Accumulated Project Patterns

Project-specific conventions, gotchas, and architecture notes live in
`.wai/resources/reflections/`. Run `wai search "<topic>"` to retrieve relevant
context before starting research or creating tickets.

> **Before research or ticket creation**: always run `wai search "<topic>"` to
> check for known patterns. Do not rediscover what is already documented.
<!-- WAI:REFLECT:REF:END -->


# Paranoid - Android App with Mini-Apps

## Purpose

A single Android app ("paranoid") that hosts many small apps. Each mini-app is a self-contained web page (HTML/CSS/JS) running inside a WebView. The hub screen lists all available mini-apps. Similar to [jams](../jams/) but as an Android app.

## Guidelines

### Structure

Each mini-app lives in its own folder at the repository root (same pattern as jams):

```
app-name/
├── index.html
├── style.css
├── script.js
├── spec/
│   └── functionality.md
└── sessions/
    └── session-YYYY-MM-DD.md
```

The Android project lives in `android/` and provides a WebView shell.

### Adding a New Mini-App

1. **Create scaffolding**:
   ```bash
   just new app-name
   ```

2. **Develop the mini-app** by editing:
   - `app-name/spec/functionality.md` - Purpose and requirements
   - `app-name/index.html` - HTML structure
   - `app-name/style.css` - Styling (dark theme)
   - `app-name/script.js` - Functionality

3. **Document the session** in `app-name/sessions/`

4. **Commit** following Conventional Commits format

The mini-app will automatically appear on the hub screen.

### Technical Constraints

- **No frameworks**: Vanilla JavaScript and CSS only for mini-apps
- **Simplicity first**: Keep code as simple as possible
- **Offline-capable**: Mini-apps run locally in WebView, no network required
- **Single page**: All functionality in one HTML file with associated CSS/JS
- **Mobile-first**: Design for phone screens (touch input, portrait orientation)

### Design Philosophy

- **Dark theme**: Dark backgrounds (`#121212`), light text — OLED-friendly
- **Minimalist**: Clean, uncluttered interfaces
- **Touch-friendly**: Large tap targets (48dp min), swipe gestures where appropriate
- **Functional**: Utility over aesthetics

### Android Project

The `android/` directory contains a Kotlin project:
- Single `MainActivity` hosting a WebView
- Web content bundled as assets at build time via `just assets`
- JavaScript bridge available for native features when needed

To build: `just build` (requires Android SDK)
To preview in browser: `just serve`

### Git Workflow

Follow **Conventional Commits**: `<type>(<scope>): <description>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Tidy First**: separate tidying from behavior changes, atomic commits.
