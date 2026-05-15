# Change: Add NN Adapt Lab mini-app

## Why
We want a low-risk exploration path for on-device neural-network features that preserves Paranoid's offline, private, resource-conscious constraints. A fixed LiteRT model plus a small local adaptive layer lets us test whether personalization improves results before attempting expensive on-device fine-tuning.

## What Changes
- Add an `nn-adapt-lab` capability for an experimental mini-app that runs fixed LiteRT inference and adapts outputs with local lightweight learning.
- Bound v1 to a tiny fixed-shape INT8 classifier/regressor model with calibration/confidence-remapping as the first adaptive strategy.
- Keep this proposal independent from NN Training Lab in v1; any future sharing should be limited to small domain contracts and comparable metric names.
- Store local examples, adaptive parameters, metrics, and model metadata on-device only, with model-version/hash compatibility checks.
- Surface baseline-vs-adapted results, latency, confidence, model metadata, and adaptation status in a minimal Android Views UI.
- Add safety controls for reset, separate metric clearing, sensitive export confirmation, experimental labeling or debug/internal gating, and bounded resource use.

## Impact
- Affected specs: `nn-adapt-lab`
- Affected code: new mini-app under `android/app/src/main/kotlin/dev/charly/paranoid/apps/nnadapt/`, launcher registry entry, manifest entry, layouts/resources, local assets/model packaging, local persistence for examples/adaptive parameters
- New dependency: LiteRT runtime for inference, pinned to `com.google.ai.edge.litert:litert:<x.y.z>` with selective-ops/builtin-ops configuration matching the chosen v1 model (exact version locked in ticket 1)
- Persistence: reuse the existing Room dependency for examples, adaptive parameters, and metrics — no parallel storage mechanism
- Size guardrail: revise the proposal if the model/runtime adds more than 25 MB to installed size on any shipped ABI split
