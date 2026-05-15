# Design: NN Adapt Lab

## Context
Paranoid mini-apps are small, offline-first Android Activities using Kotlin and Android Views. The adaptation lab explores practical on-device personalization without mutating neural-network weights.

## Goals
- Run a fixed quantized LiteRT model locally.
- Apply a deterministic adaptive layer implemented in pure Kotlin.
- Compare baseline model output with adapted output on the same input.
- Persist only local examples, derived adaptive parameters, model metadata, and run metrics.
- Keep implementation suitable for unit tests and later comparison with a true training lab.

## Non-Goals
- No full neural-network backpropagation in this app.
- No cloud inference or cloud training.
- No NNAPI integration.
- No large LLM deployment.
- No background continuous training loop.

## V1 experiment bounds
V1 SHALL use a tiny fixed-shape classifier or regressor model with an INT8 `.tflite` artifact. The model SHALL be small enough to bundle as an app asset; if the model/runtime adds more than 25 MB to installed size, the proposal must be revised before implementation continues. V1 SHOULD start with a deterministic demo dataset so tests and manual exploration can compare baseline and adapted behavior without external data.

## Release visibility
The lab is an experimental mini-app visible from the regular hub only if it is clearly labeled "Experimental" in the launcher and screen copy. If release-size or stability concerns arise during implementation, the implementation may instead gate it behind a debug/internal build flag, but that decision must be documented before tasks are marked complete.

## Architecture

```text
NnAdaptActivity
  -> InputCollector / demo dataset picker
  -> FixedModelInferenceEngine (LiteRT)
  -> AdaptiveLayer (pure Kotlin)
  -> ResultPresenter

ExampleStore
AdaptiveParameterStore
MetricsStore
```

The app owns its model and adaptive layer. The training lab is independent in v1: no shared implementation is required. If both labs later use the same example schema, shared code should be limited to small domain contracts such as `NnExample`, `NnPrediction`, and metric names.

## Adaptive layer strategy
V1 SHALL start with calibration thresholds and confidence remapping. The adaptive state is a versioned, deterministic set of parameters that can alter confidence and/or label selection according to explicit rules covered by unit tests.

Local feature statistics, nearest-neighbor memory, or an online linear head are escalation options only after the calibration approach is implemented and measured.

The adaptive layer SHALL expose deterministic inputs and outputs so unit tests can verify parameter updates and adapted predictions without Android runtime dependencies.

## Model and adaptive-state compatibility
Adaptive parameters, examples, and metrics SHALL record the model id, model version, and/or model file hash used to produce them. When the bundled model changes, incompatible adaptive parameters SHALL NOT be silently reused; the app SHALL ignore, migrate, or reset incompatible state and explain the choice to the user.

## Model packaging
The v1 exploration SHALL use a small INT8 `.tflite` model bundled in app assets. If the model becomes too large or requires runtime downloads, the proposal SHALL be revised before implementation.

## Runtime dependency pin
V1 SHALL pin the LiteRT runtime to `com.google.ai.edge.litert:litert:1.0.1` (or the latest stable equivalent at implementation time) and use the selective-ops/builtin-ops configuration that covers only the operators required by the chosen v1 model. The chosen artifact, version, and ops configuration SHALL be recorded in implementation notes alongside the measured installed-size delta per ABI split, and the proposal SHALL be revised before continuing if the delta exceeds the 25 MB guardrail on any shipped ABI.

## Persistence choice
V1 SHALL use Room for local persistence of examples, adaptive parameters, and metrics, reusing the project's existing Room dependency to avoid adding a parallel storage mechanism. Persistence DTOs SHALL stay separate from pure-Kotlin domain models so the adaptive layer remains testable without Android dependencies.

## Resource policy
Inference is user-triggered or bounded by an explicit experiment run. Continuous background inference is out of scope. The app records latency and avoids thermal-heavy training work; a simple rate limit is sufficient for v1 unless the selected experiment requires repeated inference.

## Export and privacy policy
Local examples and metrics may contain sensitive user-provided data. Any export SHALL be user-initiated, disclose that the export may contain local examples/results, and use Android sharing only after confirmation.

## Exploration success metrics
Implementation should record:
- installed-size impact of model/runtime;
- representative cold and warm inference latency;
- whether adaptation changes outputs on the demo dataset;
- reset/export behavior;
- whether the app works offline.

## Comparison with Training Lab
The adaptation lab defines its own result metrics in a way that can later be compared to `nn-training-lab`: baseline output, adapted output, confidence, latency, model id/hash, and local evaluation labels when available.
