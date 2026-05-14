# nn-adapt-lab Specification

## ADDED Requirements

### Requirement: Fixed local LiteRT inference
The system SHALL provide an experimental NN Adapt Lab mini-app that runs a bundled fixed LiteRT model locally on-device without cloud inference.

#### Scenario: Run baseline inference offline
- **GIVEN** the device is offline
- **AND** the bundled model asset is available
- **WHEN** the user runs an experiment input
- **THEN** the system produces a baseline model result locally
- **AND** the system does not require a network connection

#### Scenario: Model load failure
- **GIVEN** the bundled model cannot be loaded
- **WHEN** the user opens or runs the lab
- **THEN** the system shows a clear unavailable/error state
- **AND** the system does not crash

### Requirement: Local adaptive layer
The system SHALL apply a local adaptive layer to baseline model outputs without modifying the underlying LiteRT model weights.

#### Scenario: Empty adaptive state preserves baseline
- **GIVEN** no compatible local examples or adaptive parameters have been saved
- **WHEN** the user runs an input
- **THEN** the adapted result matches the baseline result except for explicit adaptation metadata

#### Scenario: Local examples deterministically affect adapted result
- **GIVEN** compatible local examples have produced an adaptive threshold or confidence-remapping parameter for a known output label
- **AND** a new baseline result crosses that adaptive rule
- **WHEN** the user runs the matching input
- **THEN** the system shows both the baseline result and the adapted result
- **AND** the adapted result reflects the stored adaptive rule

### Requirement: Experiment data remains local and resettable
The system SHALL store examples, adaptive parameters, run metrics, and model metadata locally and SHALL allow the user to reset adaptive state.

#### Scenario: Persist compatible adaptive state
- **GIVEN** the user has added examples that update adaptive parameters
- **WHEN** the app is closed and reopened with the same compatible model metadata
- **THEN** the adaptive parameters are restored from local storage

#### Scenario: Ignore incompatible adaptive state
- **GIVEN** saved adaptive parameters reference a different model version or model hash than the bundled model
- **WHEN** the app loads adaptive state
- **THEN** the system does not silently apply the incompatible parameters
- **AND** the system explains whether the state was ignored, reset, or migrated

#### Scenario: Reset adaptive state
- **GIVEN** the user has saved local examples and adaptive parameters
- **WHEN** the user chooses reset
- **THEN** the system clears examples and adaptive parameters
- **AND** the system records or displays that adaptation has been reset
- **AND** subsequent adapted results return to the empty-state behavior

#### Scenario: Clear metrics separately
- **GIVEN** run metrics exist
- **WHEN** the user chooses to clear metrics or all lab data
- **THEN** the system clears the selected metric records without requiring network access

### Requirement: Baseline versus adapted comparison
The system SHALL show baseline and adapted outputs together with enough metadata to compare the approaches.

#### Scenario: Compare one run
- **GIVEN** a user has run an experiment input
- **WHEN** results are displayed
- **THEN** the system shows the baseline output
- **AND** the system shows the adapted output
- **AND** the system shows confidence or score metadata when available
- **AND** the system shows inference latency or run timing metadata
- **AND** the system shows model metadata sufficient to identify the bundled model version or hash

### Requirement: Bounded exploration resource use
The system SHALL keep inference user-triggered or explicitly bounded by an experiment action and SHALL avoid unbounded background inference.

#### Scenario: No background inference loop
- **GIVEN** the user leaves the NN Adapt Lab
- **WHEN** no explicit experiment action is active
- **THEN** the system does not continue running NN inference in the background

### Requirement: Sensitive export confirmation
The system SHALL treat local examples, predictions, adaptive parameters, and metrics as potentially sensitive experiment data.

#### Scenario: Confirm export
- **GIVEN** local examples or metrics exist
- **WHEN** the user chooses export
- **THEN** the system explains that the export may contain local examples, predictions, parameters, or metrics
- **AND** the system only creates the export after explicit user confirmation
- **AND** the export is user-initiated through Android sharing
