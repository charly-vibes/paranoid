# Design: NN Training Lab

## Context
The training lab is for exploration, not the default NN integration strategy. It should answer whether on-device fine-tuning is feasible and useful under Paranoid's local-first constraints.

## Goals
- Fine-tune a tiny model or trainable head on-device from local examples.
- Make training explicit, cancellable, checkpointed, and reversible.
- Capture training metrics for comparison against fixed inference and adaptive-layer approaches.
- Protect device resources with battery and thermal policies.

## Non-Goals
- No fine-tuning of large LLMs.
- No background autonomous training.
- No cloud training or cloud inference.
- No NNAPI path.
- No production promise for trained-model quality.

## V1 experiment bounds
V1 SHALL use a tiny fixed-shape classifier or regressor model, or a tiny trainable head over fixed embeddings. It SHALL NOT train an LLM or large vision model. The selected model, runtime, dataset shape, and signatures must be proven with a minimal Android train/infer cycle before UI work proceeds.

If the trainable runtime/model/checkpoint path adds more than 50 MB to installed size, the proposal must be revised before implementation continues.

## Release visibility
The lab is an experimental mini-app visible from the regular hub only if it is clearly labeled "Experimental" in the launcher and screen copy. Because this lab performs real training, implementation may choose a debug/internal build flag instead. The choice SHALL be recorded in code via a named build flag (for example `BuildConfig.DEBUG` or an explicitly named feature flag) so reviewers can verify reachability in release builds; UI ordering alone is not sufficient gating.

## Architecture

```text
NnTrainingLabActivity
  -> DatasetManager
  -> TrainingRunController
  -> TrainableModelEngine
  -> CheckpointStore
  -> TrainingMetricsStore
  -> ThermalTrainingPolicy
```

Training is a foreground, user-triggered flow. The app records progress and either commits a new checkpoint atomically or rolls back to the previous known-good state.

## Trainable model strategy
Prefer a tiny model exported ahead of time with explicit training-related signatures, such as:

- `infer` for prediction;
- `train` for one or more bounded training steps;
- `save` for checkpointing;
- `restore` for loading a checkpoint.

Runtime feasibility is a formal gate. If no supported Android runtime can cleanly execute `train`, `infer`, `save`, and `restore` for the selected tiny model, implementation SHALL stop and the change SHALL be revised rather than adding ad-hoc native training code.

## Dataset readiness
The implementation SHALL define task-specific readiness before enabling training: minimum valid example count, required label/class coverage if classification is used, maximum dataset size, supported input shape, and user-presentable reasons for rejected examples.

Persisted datasets SHALL be tagged with the bundled model id/version (or input-shape and label-set hash) under which they were collected. On app start, examples whose schema does not match the current bundled model SHALL be excluded from training and surfaced to the user with explicit clear/export actions; silent reuse is prohibited.

## Checkpoint policy
Checkpoints SHALL include model id, model version and/or model file hash, dataset summary, training run id, and commit status. Checkpoint writes SHALL be atomic: write a temporary artifact, validate it, then rename/commit it while preserving the previous checkpoint until the new one is valid. Incompatible checkpoints SHALL NOT be silently restored after a bundled model change.

## Resource policy
Training SHALL require explicit user action. Before starting, the app checks battery and thermal conditions. During training, the app monitors cancellation, iteration bounds, and resource policy. Training results are not committed unless the run completes successfully and passes basic validation.

Battery threshold: training SHALL be blocked when battery is below 40% and the device is not charging. This threshold applies both at start and during the run.

Android thermal APIs are not uniformly available across API 26-35. When thermal status/headroom APIs are unavailable, the policy SHALL use conservative fallback constraints: battery at or above 40% or active charging, explicit user confirmation, short step limits, and visible copy that thermal telemetry is unavailable.

V1 uses stop-only behavior: when battery or thermal policy becomes unsafe during a run, the run SHALL be cancelled and the previous checkpoint preserved. Pause/resume is out of scope for v1.

## Export and privacy policy
Local datasets, predictions, checkpoints metadata, and metrics may contain sensitive user-provided data. Any export SHALL be user-initiated, disclose that sensitivity, and proceed only after confirmation.

## Exploration success metrics
Implementation should record:
- installed-size impact of runtime/model/checkpoints;
- representative training duration for the bounded demo run;
- pre-training and post-training evaluation behavior;
- inference latency before and after training;
- cancellation, reset, rollback, and offline behavior;
- battery/thermal policy observations.

## Comparison with NN Adapt Lab
The lab should preserve comparable metrics: baseline output before training, trained output after training, loss/accuracy where available, latency, training duration, model id/hash, and checkpoint metadata. The training lab remains independent in v1; shared contracts with NN Adapt Lab should remain small and domain-level if introduced later.
