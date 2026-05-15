---
date: 2026-05-15
project: netmap
phase: implement
---

# Session Handoff

## What Was Done

Reviewed the `add-nn-training-lab` OpenSpec change proposal and applied
six tightening fixes in response to the review:

1. Removed the ambiguous "explicit commit rule" loophole; replaced
   with stop-only cancellation + mandatory rollback when battery or
   thermal becomes unsafe mid-run.
2. Pinned battery threshold to **40% (or charging)** in spec, design,
   and tasks — previously "agreed" but unspecified.
3. Added a normative `Lab visibility decision is recorded`
   requirement with two scenarios (release-visible + Experimental
   labeling, or debug-only via named build flag).
4. Dropped pause/resume from v1 — committed to stop-only behavior.
5. Added a normative `Isolation from NN Adapt Lab` requirement
   enforced by an automated module-dependency check, plus a
   network-API assertion for the training code path.
6. Required persisted datasets to be tagged with bundled model
   id/version (or input-shape and label-set hash); incompatible
   examples must be excluded and surfaced with clear/export
   actions instead of silent reuse.

Tasks file extended (8 → 8 tickets, but tasks 5.7-5.8, 6.9-6.10, 8.1-8.4
rewritten/added). Total tasks: 64 → 68.

Validated: `openspec validate add-nn-training-lab --strict` passes.

Committed and pushed as `cf7a73f` to `origin/main`.

## Key Decisions

- **Stop-only over pause/resume** for v1 unsafe-state handling.
  Pause interacts non-trivially with checkpoint atomicity; defer.
- **Battery threshold = 40% or charging** as a single number in
  the spec so it's testable from the spec alone.
- **Visibility gate enforced in code via a named build flag**, not
  UI ordering — so reviewers can verify reachability in release
  builds.
- **Module-dependency check is mandatory**, not advisory. The
  isolation requirement uses a Gradle/module-graph assertion so
  drift fails the build.

## Gotchas & Surprises

- Only one wai project exists (`netmap`), so this handoff lives
  under netmap even though the work was on a different OpenSpec
  proposal. Consider creating a `paranoid-openspec` project or
  similar if NN-lab work continues.
- `wai close` flagged `.claude/settings.local.json` as uncommitted;
  intentionally left unstaged (user-local).

## What Took Longer Than Expected

Nothing notable — straightforward review + edit cycle.

## Open Questions

- Should the NN training-lab work get its own wai project once
  implementation begins?
- The proposal still leaves "release-visible vs debug-only" as an
  implementation-time choice; reviewers may want it pinned now.

## Next Steps

1. Get `add-nn-training-lab` proposal approved.
2. When implementation starts, ticket 1 (feasibility gate) is the
   blocker — must prove LiteRT trainable signatures work before
   anything else.
3. Consider running the same review pass on `add-nn-adapt-lab`
   (also 0/48, scaffolded same day).

## Context

### git_status

```
 M .claude/settings.local.json
```

### open_issues

```
○ PARANOID-24g ● P3 UsageAudit: verify charging-transition wording on device
○ PARANOID-mup ● P3 NetDiag: Instrumentation tests for exchange
○ PARANOID-wbv ● P3 NetDiag: Instrumentation test for SnapshotCaptureEngine
○ PARANOID-xsz ● P3 UsageAudit: write instrumentation/UI tests for permission gating, empty states, screen flows

--------------------------------------------------------------------------------
Total: 4 issues (4 open, 0 in progress)

Status: ○ open  ◐ in_progress  ● blocked  ✓ closed  ❄ deferred
```
