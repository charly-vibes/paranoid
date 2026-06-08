## ADDED Requirements

### Requirement: Streaming Export
The system SHALL export a session by streaming events to the output file in
bounded pages, without loading all events or the full output into memory at once.

#### Scenario: Large session export stays bounded
- **WHEN** a session with many events is exported
- **THEN** events are read in pages and written incrementally to the file
- **AND** the app does not accumulate the entire dataset or output string in memory

#### Scenario: JSON output is compact and escaped
- **WHEN** a session is exported as JSON
- **THEN** the output is compact (not pretty-printed)
- **AND** string fields are JSON-escaped and non-finite numbers are written as null

### Requirement: Export Volume Awareness
The system SHALL show the event count and an estimated file size for each format
before the user starts an export.

#### Scenario: Picker shows size estimate
- **WHEN** the user opens the export picker for a session
- **THEN** each format option displays an estimated output size derived from the event count

### Requirement: Export Progress Feedback
The system SHALL display cancellable progress while an export runs and share the
file only after the export completes successfully.

#### Scenario: Progress shown during export
- **WHEN** an export is running
- **THEN** the user sees events-written / total progress
- **AND** can cancel the export

#### Scenario: Cancel cleans up
- **WHEN** the user cancels an in-progress export
- **THEN** the partial output file is removed and no share sheet is launched

### Requirement: Export Resource Safety
The system SHALL avoid producing corrupt or oversized exports by writing to a
temporary file renamed on success and by refusing exports that would exceed
available storage.

#### Scenario: Insufficient space refused
- **WHEN** the estimated export size exceeds available free space with margin
- **THEN** the export is refused with a clear message and no file is written

#### Scenario: Failed export leaves no partial file
- **WHEN** an export fails or is cancelled mid-write
- **THEN** the temporary file is deleted and the previous export (if any) is untouched
