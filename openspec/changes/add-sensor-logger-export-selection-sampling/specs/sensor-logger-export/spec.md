## ADDED Requirements

### Requirement: Export Sensor Selection
The system SHALL let the user choose which sensor types to include in an export,
defaulting to all sensors present in the session, and SHALL refuse an export with
no sensors selected.

#### Scenario: Export a subset of sensors
- **WHEN** the user selects only some sensor types and exports
- **THEN** the output contains events only for the selected sensor types

#### Scenario: No sensor selected
- **WHEN** the user deselects every sensor type
- **THEN** the export action is unavailable or refused with a clear message

### Requirement: Export Downsampling
The system SHALL let the user thin the exported data per sensor type using a
sampling strategy: all samples, keep 1 of every N events, or keep at most one
sample per time interval.

#### Scenario: Keep one of every N
- **WHEN** the user picks "1 of N" sampling
- **THEN** for each sensor type only every Nth recorded event is written

#### Scenario: Keep one per interval
- **WHEN** the user picks an interval of T milliseconds
- **THEN** for each sensor type at most one event is written per T-millisecond window

#### Scenario: Accurate JSON count under sampling
- **WHEN** a sampled session is exported as JSON
- **THEN** the JSON `event_count` equals the number of events actually written

### Requirement: Export Estimate Reflects Selection
The system SHALL show an estimated output size that updates as the user changes
the sensor selection and sampling strategy.

#### Scenario: Estimate updates with sampling
- **WHEN** the user changes the selection or sampling in the export config
- **THEN** the displayed estimated size updates accordingly
