# nn-training-lab Specification

## ADDED Requirements

### Requirement: Explicit on-device training lab
The system SHALL provide an experimental NN Training Lab mini-app that performs user-triggered on-device training or fine-tuning of a tiny trainable model using local examples.

#### Scenario: Start a training run
- **GIVEN** the user has enough valid local training examples according to the task-specific readiness rule
- **AND** battery and thermal policy allows training
- **WHEN** the user starts training
- **THEN** the system runs a bounded on-device training process
- **AND** the system shows progress or status updates
- **AND** the system does not require a network connection

#### Scenario: Training unavailable
- **GIVEN** the trainable model artifact or runtime is unavailable
- **WHEN** the user opens the lab
- **THEN** the system shows a clear unavailable/error state
- **AND** the system does not crash

#### Scenario: Runtime feasibility gate fails
- **GIVEN** no supported Android runtime can execute the selected model's train, infer, save, and restore operations
- **WHEN** implementation reaches the feasibility gate
- **THEN** the implementation stops before UI-dependent training work proceeds
- **AND** the change is revised rather than adding ad-hoc native training code

### Requirement: Trainable model signatures
The system SHALL use a model/runtime path that supports explicit inference, training, saving, and restoring of model state.

#### Scenario: Infer before and after training
- **GIVEN** a trainable model is available
- **WHEN** the user runs evaluation before and after a completed training run
- **THEN** the system can produce pre-training and post-training outputs for comparison

#### Scenario: Save and restore trained state
- **GIVEN** a training run completes successfully
- **WHEN** the app saves and later restores compatible model state
- **THEN** subsequent inference uses the restored trained state

#### Scenario: Incompatible trained state
- **GIVEN** saved trained state references a different model version or model hash than the bundled model
- **WHEN** the app attempts to restore model state
- **THEN** the system does not silently restore the incompatible state
- **AND** the system explains whether the state was ignored, reset, or migrated

### Requirement: Bounded local datasets
The system SHALL validate local training examples before training and SHALL enforce task-specific dataset bounds.

#### Scenario: Insufficient dataset blocks training
- **GIVEN** the local dataset does not meet minimum example count, label coverage, or input-shape requirements
- **WHEN** the user views training readiness
- **THEN** the system disables or blocks training
- **AND** the system shows user-presentable reasons

#### Scenario: Dataset limit reached
- **GIVEN** the local dataset has reached the configured maximum count or storage budget
- **WHEN** the user attempts to add more examples
- **THEN** the system rejects or asks the user to clear/export data before adding more

### Requirement: Checkpoint and rollback
The system SHALL checkpoint model state after successful training and SHALL allow rollback or reset to a prior state.

#### Scenario: Successful checkpoint
- **GIVEN** a training run completes successfully
- **WHEN** the system commits the run
- **THEN** the system saves checkpoint metadata and model state locally
- **AND** the checkpoint metadata includes model identity/version or hash and commit status

#### Scenario: Atomic checkpoint commit
- **GIVEN** a training run is ready to save a checkpoint
- **WHEN** the checkpoint is written
- **THEN** the system writes and validates a temporary checkpoint before committing it
- **AND** the previous checkpoint remains available until the new checkpoint is valid

#### Scenario: Failed run rolls back
- **GIVEN** a previous checkpoint exists
- **WHEN** a training run fails or is cancelled before commit
- **THEN** the system restores or keeps the previous checkpoint
- **AND** the failed run is marked as not committed

#### Scenario: Reset to initial model
- **GIVEN** one or more trained checkpoints exist
- **WHEN** the user chooses reset
- **THEN** the system returns inference to the initial model state
- **AND** checkpoint metadata reflects the reset

### Requirement: Training resource policy
The system SHALL gate training with battery and thermal policy and SHALL stop or pause training when policy becomes unsafe.

#### Scenario: Severe thermal state blocks training
- **GIVEN** the device reports severe thermal status or equivalent policy input
- **WHEN** the user attempts to start training
- **THEN** the system prevents the training run
- **AND** the system explains why training is unavailable

#### Scenario: Thermal API unavailable
- **GIVEN** the device or API level does not expose supported thermal telemetry
- **WHEN** the user attempts to start training
- **THEN** the system applies conservative fallback policy using battery level, charging state, explicit confirmation, and bounded step limits
- **AND** the system explains that thermal telemetry is unavailable

#### Scenario: Unsafe state during training
- **GIVEN** a training run is active
- **WHEN** battery or thermal policy becomes unsafe
- **THEN** the system stops or pauses training safely
- **AND** the system does not commit a partial unsafe checkpoint unless the run meets the explicit commit rule

### Requirement: Training metrics and comparison
The system SHALL record training metrics and show before/after behavior for each completed run.

#### Scenario: Completed training metrics
- **GIVEN** a training run completes
- **WHEN** results are displayed
- **THEN** the system shows training duration
- **AND** the system shows loss, accuracy, or the selected task-specific quality metric when available
- **AND** the system shows pre-training and post-training outputs for comparable evaluation inputs
- **AND** the system shows model metadata sufficient to identify the bundled model version or hash

### Requirement: Local datasets and sensitive exports
The system SHALL store datasets and metrics locally, keep them user-clearable, and allow user-initiated export for inspection.

#### Scenario: Persist local training examples
- **GIVEN** the user has added training examples
- **WHEN** the app is closed and reopened
- **THEN** the examples remain available from local storage

#### Scenario: Clear local training data
- **GIVEN** local examples, checkpoints, or metrics exist
- **WHEN** the user chooses the relevant clear/reset action
- **THEN** the system clears the selected local data without requiring network access

#### Scenario: Confirm export of sensitive experiment data
- **GIVEN** local examples, checkpoints metadata, or training metrics exist
- **WHEN** the user chooses export
- **THEN** the system explains that the export may contain local examples, predictions, checkpoint metadata, or metrics
- **AND** the system only creates the export after explicit user confirmation
- **AND** the export is user-initiated through Android sharing
