# screen-time-overlay Specification

## Purpose
Show a thin system overlay progress bar over all apps that fills toward the next checkpoint,
giving the user a continuous passive awareness of their session length.

## ADDED Requirements

### Requirement: Overlay progress bar rendering
The system SHALL draw a full-width, 4 dp tall bar immediately below the system status bar
using `WindowManager TYPE_APPLICATION_OVERLAY`. The bar fills from left to right, reaching
100% at each checkpoint. The bar does not receive or intercept touch input.

#### Scenario: Bar visible during active session
- **GIVEN** monitoring is active and a session is in progress
- **WHEN** any app is in the foreground
- **THEN** the overlay bar is visible above all app content
- **AND** the bar shows progress proportional to elapsed time toward the next checkpoint

#### Scenario: Bar hidden when screen is off
- **GIVEN** monitoring is active
- **WHEN** the screen turns off (beyond the debounce threshold)
- **THEN** the overlay bar is removed from the window
- **AND** no overlay is drawn until the next session starts

#### Scenario: Bar resets on each checkpoint
- **GIVEN** the bar has filled to 100% at a checkpoint
- **WHEN** the checkpoint notification fires
- **THEN** the bar resets to 0% and begins filling toward the next checkpoint

#### Scenario: Bar fill restored after service restart
- **GIVEN** a session is active and the monitoring service is restarted by the OS
- **WHEN** the overlay bar is re-attached to the window
- **THEN** the bar initialises at the correct fill fraction for the current elapsed session time
- **AND** the bar does not reset to 0%

#### Scenario: Overlay is non-interactive
- **GIVEN** the overlay bar is visible
- **WHEN** the user taps or swipes in the region occupied by the bar
- **THEN** the touch event passes through to the underlying app
- **AND** the bar does not consume or intercept the event

### Requirement: Overlay permission gate
The system SHALL check for `SYSTEM_ALERT_WINDOW` permission before drawing the overlay.
If the permission has not been granted, the system SHALL show a clear explanation in
`ScreenTimeActivity` and a button that deep-links to the system Special app access settings.

#### Scenario: Permission not yet granted on first launch
- **GIVEN** the user opens `ScreenTimeActivity` for the first time
- **WHEN** `SYSTEM_ALERT_WINDOW` has not been granted
- **THEN** the system shows an explanation of why the overlay permission is needed
- **AND** provides a button that opens the system Manage overlay permission screen for this app
- **AND** monitoring can be started without the permission; the overlay bar will not appear until the permission is granted

#### Scenario: Permission granted, monitoring starts
- **GIVEN** `SYSTEM_ALERT_WINDOW` has been granted
- **WHEN** the user starts monitoring
- **THEN** the system begins drawing the overlay bar for subsequent sessions
- **AND** no permission prompt is shown again

#### Scenario: Permission revoked while monitoring is active
- **GIVEN** monitoring is active with the overlay visible
- **WHEN** the user revokes `SYSTEM_ALERT_WINDOW` via system settings
- **THEN** the system stops attempting to draw the overlay
- **AND** monitoring continues (sessions and notifications remain active)
- **AND** the activity shows a warning that the overlay is unavailable

#### Scenario: Permission re-granted while monitoring is active
- **GIVEN** monitoring is active and the overlay is absent due to missing permission
- **WHEN** the user grants `SYSTEM_ALERT_WINDOW` via system settings and the screen turns on for the next session
- **THEN** the overlay bar appears at the correct fill fraction for the current elapsed session time
- **AND** no restart of monitoring is required
