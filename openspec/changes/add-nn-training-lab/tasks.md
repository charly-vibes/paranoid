## Suggested implementation order
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8

## 1. Ticket: Confirm trainable model and runtime feasibility
**Goal:** prove the selected tiny model can be trained on Android before building UI around it.

- [ ] 1.1 Choose the v1 tiny fixed-shape classifier/regressor task, dataset shape, and trainable model architecture.
- [ ] 1.2 Confirm the Android runtime supports the required train/infer/save/restore signatures in a minimal Android train/infer cycle.
- [ ] 1.3 Define maximum dataset size, epoch/step limits, checkpoint size, installed-size budget, and rollback behavior.
- [ ] 1.4 Define metrics: pre-training result, post-training result, loss/accuracy when available, training duration, inference latency, and model id/hash.
- [ ] 1.5 Decide whether the lab is release-visible with Experimental labeling or debug/internal only.

**Acceptance criteria:**
- [ ] 1.6 A minimal trainable model artifact, signature contract, runtime feasibility result, visibility choice, and size impact are documented before app implementation begins.

## 2. Ticket: Build dataset and validation domain logic
**Goal:** keep training inputs deterministic and testable.

- [ ] 2.1 **Red:** write a failing unit test for validating accepted training examples.
- [ ] 2.2 **Green:** implement the smallest dataset validation logic.
- [ ] 2.3 **Red:** write a failing unit test for rejecting malformed or unsupported examples.
- [ ] 2.4 **Green:** implement rejection with user-presentable reasons.
- [ ] 2.5 **Red:** write a failing unit test for enforcing dataset size and class/label minimums.
- [ ] 2.6 **Green:** implement the smallest training-readiness check.
- [ ] 2.7 **Refactor:** isolate dataset rules from Android storage/UI.

## 3. Ticket: Implement trainable model engine seam
**Goal:** wrap training runtime calls behind a replaceable interface.

- [ ] 3.1 **Red:** write a failing test using a fake `TrainableModelEngine` for infer-before-train flow.
- [ ] 3.2 **Green:** implement engine interface and orchestration boundary.
- [ ] 3.3 **Red:** write a failing test for a bounded training run with progress events.
- [ ] 3.4 **Green:** implement `TrainingRunController` with fake engine support.
- [ ] 3.5 **Red:** write a failing test for cancellation.
- [ ] 3.6 **Green:** implement cancellation handling.
- [ ] 3.7 Add the runtime dependency and trainable model asset only after the fake-engine flow is covered.
- [ ] 3.8 **Manual:** verify one train/infer/save/restore cycle on a device/emulator.
- [ ] 3.9 Stop and revise the proposal if the selected runtime cannot support the required signatures cleanly.

## 4. Ticket: Add checkpoints and rollback
**Goal:** make training reversible.

- [ ] 4.1 **Red:** write a failing test for saving a checkpoint after successful training with model id/version/hash metadata.
- [ ] 4.2 **Green:** implement checkpoint persistence.
- [ ] 4.3 **Red:** write a failing test for atomic checkpoint commit that preserves the previous checkpoint until the new checkpoint validates.
- [ ] 4.4 **Green:** implement temp-write, validate, and rename/commit behavior.
- [ ] 4.5 **Red:** write a failing test for restoring the previous checkpoint after a failed run.
- [ ] 4.6 **Green:** implement rollback.
- [ ] 4.7 **Red:** write a failing test for rejecting incompatible checkpoint metadata after model changes.
- [ ] 4.8 **Green:** implement checkpoint compatibility checks.
- [ ] 4.9 **Red:** write a failing test for reset-to-initial-model behavior.
- [ ] 4.10 **Green:** implement reset.
- [ ] 4.11 **Refactor:** keep model artifacts, checkpoint metadata, and run metrics separate.

## 5. Ticket: Add battery and thermal training policy
**Goal:** prevent unsafe or wasteful training runs.

- [ ] 5.1 **Red:** write a failing unit test for denying training when policy inputs indicate severe thermal status.
- [ ] 5.2 **Green:** implement the smallest `ThermalTrainingPolicy`.
- [ ] 5.3 **Red:** write a failing unit test for denying training when battery is below 40% and the device is not charging.
- [ ] 5.4 **Green:** implement battery gating at the 40%/charging threshold.
- [ ] 5.5 **Red:** write a failing unit test for fallback behavior when thermal telemetry is unavailable on older/simpler devices.
- [ ] 5.6 **Green:** implement conservative fallback policy requiring battery at or above 40% or active charging, explicit confirmation, and bounded step limits.
- [ ] 5.7 **Red:** write a failing test for cancelling the run when status becomes unsafe during training and rolling back to the previous checkpoint (stop-only; no pause/resume in v1).
- [ ] 5.8 **Green:** integrate policy with `TrainingRunController` using stop-only behavior.
- [ ] 5.9 **Manual:** verify policy copy and behavior on a real device where possible.

## 6. Ticket: Persist datasets and metrics
**Goal:** keep experiments reproducible locally.

- [ ] 6.1 **Red:** write a failing persistence test for training examples.
- [ ] 6.2 **Green:** implement local dataset persistence.
- [ ] 6.3 **Red:** write a failing persistence test for training run metrics.
- [ ] 6.4 **Green:** implement metrics persistence.
- [ ] 6.5 **Red:** write a failing test for clearing selected local datasets, checkpoints, and metrics.
- [ ] 6.6 **Green:** implement clear/reset actions for local data.
- [ ] 6.7 **Red:** write a failing export test requiring sensitive-data confirmation for datasets/metrics.
- [ ] 6.8 **Green:** implement local export disclosure and Android sharing patterns.
- [ ] 6.9 **Red:** write a failing test for excluding persisted examples whose schema (input shape or label set) does not match the current bundled model and surfacing a user-presentable explanation with clear/export actions.
- [ ] 6.10 **Green:** tag persisted examples with bundled model id/version (or input-shape and label-set hash) and implement the schema-mismatch flow.
- [ ] 6.11 **Refactor:** remove duplicate mapping with checkpoint metadata if it appears.

## 7. Ticket: Build minimal Android Views UI
**Goal:** expose training lab as a clearly experimental mini-app.

- [ ] 7.1 **Red:** write a failing UI/instrumentation test for opening NN Training Lab from the hub.
- [ ] 7.2 **Green:** add Activity, manifest entry, launcher registry entry, and minimal layout.
- [ ] 7.3 **Red:** write a failing UI/instrumentation test for training readiness and disabled start state.
- [ ] 7.4 **Green:** render dataset status, policy status, and start-training control.
- [ ] 7.5 **Red:** write a failing UI/instrumentation test for progress, completion, and rollback/reset controls.
- [ ] 7.6 **Green:** render progress, metrics, checkpoint state, rollback, and reset.
- [ ] 7.7 **Refactor:** simplify UI state model and wording.

## 8. Ticket: Validate isolation, visibility, and comparison
**Goal:** make sure the training lab stays separate from the adaptation lab, has its visibility recorded in code, and enables comparison.

- [ ] 8.1 Add an automated check (Gradle dependency assertion, module-graph test, or equivalent) that fails the build if the training lab module/package depends on the NN Adapt Lab module/package.
- [ ] 8.2 Add an automated check (dependency or manifest-permission assertion) that fails the build if the training code path pulls in Internet, network, or remote-inference APIs.
- [ ] 8.3 Record the visibility decision in code via a named build flag (e.g. `BuildConfig.DEBUG` or an explicitly named feature flag), and add a test asserting the launcher entry's reachability matches that flag in release builds.
- [ ] 8.4 If the visibility decision is "release-visible", add a test asserting the launcher entry and the lab's first screen carry "Experimental" labeling.
- [ ] 8.5 Verify both labs can report comparable baseline/trained/adapted metrics where their example contracts overlap.
- [ ] 8.6 Run unit tests and relevant instrumentation tests.
- [ ] 8.7 Manually verify offline operation, cancellation, reset, and rollback.
- [ ] 8.8 Record installed-size impact, representative training duration, inference latency, and policy observations.
