## ADDED Requirements

### Requirement: GeoJSON Export
The system SHALL export a recording as a GeoJSON FeatureCollection where each measurement is a Point feature with properties: timestamp, accuracy, speed, network_type, rsrp, rsrq, cell_id, pci, signal_level.

#### Scenario: Export recording as GeoJSON
- **WHEN** the user chooses Export > GeoJSON from a recording detail
- **THEN** a .geojson file is generated and the Android share sheet opens

### Requirement: CSV Export
The system SHALL export a recording as a CSV file with columns: timestamp, lat, lng, accuracy_m, speed_kmh, network_type, rsrp, rsrq, rssi, cell_id, pci, signal_level.

#### Scenario: Export recording as CSV
- **WHEN** the user chooses Export > CSV from a recording detail
- **THEN** a .csv file is generated and the Android share sheet opens

### Requirement: KML Export
The system SHALL export a recording as a KML document with placemarks for each measurement, styled by signal level.

#### Scenario: Export recording as KML
- **WHEN** the user chooses Export > KML from a recording detail
- **THEN** a .kml file is generated and the Android share sheet opens

### Requirement: GPX Export
The system SHALL export a recording as a GPX track with measurement data in extension elements.

#### Scenario: Export recording as GPX
- **WHEN** the user chooses Export > GPX from a recording detail
- **THEN** a .gpx file is generated and the Android share sheet opens

### Requirement: Share via FileProvider
The system SHALL share exported files via FileProvider and ACTION_SEND intent, allowing the user to send the file through any installed sharing app.

#### Scenario: Share exported file
- **WHEN** an export file is generated
- **THEN** the Android share sheet shows available apps (Drive, Gmail, WhatsApp, etc.)
