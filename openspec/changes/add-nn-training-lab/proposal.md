# Change: Add NN Training Lab mini-app

## Why
We want a deliberately isolated exploration path for true on-device neural-network fine-tuning. Keeping this separate from the LiteRT adaptive-layer lab allows riskier training experiments without compromising the simpler, production-realistic personalization path.

## What Changes
- Add an `nn-training-lab` capability for an experimental mini-app that performs explicit user-triggered on-device training/fine-tuning of a tiny trainable model.
- Bound v1 to a tiny fixed-shape classifier/regressor or trainable-head experiment; no LLM or large vision-model training.
- Require a runtime feasibility gate for train/infer/save/restore before UI-dependent training work proceeds.
- Require model-version/hash-aware train/infer/save/restore flows, atomic checkpointing, rollback, and training metrics.
- Add thermal/battery gating before training starts and during training runs, including conservative fallback behavior when thermal APIs are unavailable.
- Keep this proposal independent from NN Adapt Lab in v1 while preserving comparable metric names where practical.
- Make the training lab clearly experimental and resettable, with sensitive export confirmation and release/debug visibility documented during implementation.

## Impact
- Affected specs: `nn-training-lab`
- Affected code: new mini-app under `android/app/src/main/kotlin/dev/charly/paranoid/apps/nntrainlab/`, launcher registry entry, manifest entry, layouts/resources, trainable model asset packaging, local persistence for datasets/checkpoints/metrics
- New dependency likely: LiteRT runtime with trainable-signature support or equivalent on-device training runtime
- Size guardrail: revise the proposal if the trainable runtime/model/checkpoint path adds more than 50 MB to installed size
