## Suggested implementation order
1 → 2 → 3 → 4 → 5 → 6 → 7

## 1. Ticket: Lock experiment contract and model choice
**Goal:** define the smallest useful local inference experiment before coding.

- [ ] 1.1 Choose the v1 tiny fixed-shape classifier/regressor task, input shape, output labels, and bundled INT8 LiteRT model.
- [ ] 1.2 Define the canonical `NnExample` and `NnPrediction` domain models used by tests; keep any future sharing with Training Lab domain-only.
- [ ] 1.3 Define baseline-vs-adapted metrics: confidence, label/result, latency, model id/hash, and optional correctness when a label is known.
- [ ] 1.4 Define adaptive reset, separate metric clearing, sensitive export confirmation, and model-version/hash compatibility behavior.
- [ ] 1.5 Decide whether the lab is release-visible with Experimental labeling or debug/internal only.
- [ ] 1.6 Define installed-size budget verification for the model/runtime.

**Acceptance criteria:**
- [ ] 1.7 The v1 model, input/output contract, metric names, visibility choice, size impact, and compatibility policy are documented in the mini-app spec/functionality note or implementation notes.

## 2. Ticket: Build pure domain adaptation logic
**Goal:** implement the adaptive layer without Android dependencies.

- [ ] 2.1 **Red:** write a failing unit test for creating an empty adaptive state.
- [ ] 2.2 **Green:** implement the smallest adaptive state and no-op adaptation path.
- [ ] 2.3 **Red:** write a failing unit test for updating adaptive state from a labeled local example.
- [ ] 2.4 **Green:** implement the smallest update rule.
- [ ] 2.5 **Red:** write a failing unit test showing baseline and adapted predictions diverge when a stored calibration threshold or confidence-remapping rule applies.
- [ ] 2.6 **Green:** implement the smallest deterministic calibration/confidence-remapping behavior.
- [ ] 2.7 **Red:** write a failing unit test proving incompatible model metadata prevents adaptive state reuse.
- [ ] 2.8 **Green:** implement model metadata compatibility checks.
- [ ] 2.9 **Refactor:** tidy naming and isolate math from storage/UI.

## 3. Ticket: Add LiteRT inference seam
**Goal:** run fixed local model inference behind a replaceable interface.

- [ ] 3.1 **Red:** write a failing unit test for input normalization/tensor mapping.
- [ ] 3.2 **Green:** implement the smallest input mapper.
- [ ] 3.3 **Red:** write a failing test with a fake `FixedModelInferenceEngine` for baseline prediction flow.
- [ ] 3.4 **Green:** implement the engine interface and app service orchestration.
- [ ] 3.5 Add LiteRT dependency and bundled model asset only after the seam is covered by tests.
- [ ] 3.6 **Manual:** verify model inference on a device/emulator and record first-run latency.

## 4. Ticket: Persist local examples, adaptive state, and metrics
**Goal:** keep exploration data local and resettable.

- [ ] 4.1 **Red:** write a failing persistence test for storing and reading local examples.
- [ ] 4.2 **Green:** implement the smallest Room or file-backed store.
- [ ] 4.3 **Red:** write a failing test for storing and restoring adaptive parameters with model id/version/hash metadata.
- [ ] 4.4 **Green:** implement adaptive parameter persistence and compatibility filtering.
- [ ] 4.5 **Red:** write a failing test for recording run metrics with model metadata.
- [ ] 4.6 **Green:** implement local metric persistence.
- [ ] 4.7 **Red:** write a failing test for clearing metrics separately from adaptive state.
- [ ] 4.8 **Green:** implement separate metric clearing.
- [ ] 4.9 **Refactor:** keep persistence DTOs separate from domain models if conversion logic emerges.

## 5. Ticket: Build minimal Android Views UI
**Goal:** expose the experiment from the launcher.

- [ ] 5.1 **Red:** write a failing UI/instrumentation test for opening NN Adapt Lab from the hub.
- [ ] 5.2 **Green:** add Activity, manifest entry, launcher registry entry, and minimal layout.
- [ ] 5.3 **Red:** write a failing UI/instrumentation test for baseline/adapted result rendering with a fake engine.
- [ ] 5.4 **Green:** render input, baseline output, adapted output, confidence, latency, and adaptation status.
- [ ] 5.5 **Refactor:** simplify view binding and copy.

## 6. Ticket: Add reset and export controls
**Goal:** make experiments inspectable and reversible.

- [ ] 6.1 **Red:** write a failing test for clearing examples and adaptive parameters.
- [ ] 6.2 **Green:** implement reset.
- [ ] 6.3 **Red:** write a failing test for requiring confirmation before exporting examples/metrics as local structured text or CSV.
- [ ] 6.4 **Green:** implement sensitive export disclosure and existing Android sharing patterns.
- [ ] 6.5 **Manual:** verify export contains no network transfer and reset returns the app to baseline behavior.

## 7. Ticket: Validate resource and privacy constraints
**Goal:** ensure the lab remains safe for inclusion in Paranoid.

- [ ] 7.1 Add tests or static checks proving the adaptation path does not require network APIs.
- [ ] 7.2 Run unit tests and relevant instrumentation tests.
- [ ] 7.3 Manually verify offline operation.
- [ ] 7.4 Record observed model/runtime size impact and representative cold/warm inference latency.
- [ ] 7.5 Verify the hub/screen copy clearly labels the lab Experimental, or document debug/internal gating.
