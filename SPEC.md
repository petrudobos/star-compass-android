# Star Compass — Specification Document

## Overview

**Star Compass** (internal codename: *Star Compass*) is an Android augmented reality (AR) application that overlays celestial objects (stars, Sun, Moon, constellations) onto a live camera view of the sky. The app uses GPS for location and the device's compass/gyroscope for orientation to accurately render the night sky in real-time.

---

## Features

### Core Features

| Feature | Description |
|---------|-------------|
| **AR Sky View** | Live camera feed with real-time star overlay using CameraX |
| **GPS Integration** | Uses fused location provider for accurate latitude/longitude |
| **Compass + Gyroscope** | Geomagnetic rotation vector sensor for device orientation tracking |
| **Real-time Celestial Calculation** | Sun, Moon, and star positions computed using astronomical algorithms |
| **Constellation Visualization** | Major constellations drawn with connecting lines and labels |
| **Cardinal Direction Markers** | N, E, S, W indicators projected onto the horizon |
| **Dark Mode Toggle** | Switch between camera view and dark background for better star visibility |
| **Screenshot Capture** | Save current view (camera + overlay) to Pictures/StarCompass folder |
| **Interactive Star Labels** | Tap constellation names to open Wikipedia articles |

### Orientation & Calibration

| Feature | Description |
|---------|-------------|
| **Compass Calibration Dialog** | Guided figure-8 calibration instructions for magnetometer |
| **Haptic Feedback** | Vibration confirmation when calibration completes |
| **AR Alignment Test Guide** | Built-in tutorial for verifying AR accuracy |
| **Compass Issues Helper** | Explains magnetic interference and hardware limitations |

### Data & Persistence

| Feature | Description |
|---------|-------------|
| **Star Database** | Room database with pre-seeded bright star catalog |
| **Repository Pattern** | Clean architecture with StarRepository for data access |
| **Flow-based Updates** | Kotlin Flow for reactive UI updates |

---

## Screens

### 1. Main Sky View (MainActivity)

The primary screen displaying the AR sky overlay.

#### Layout Structure

```
┌─────────────────────────────────────────┐
│     [Calibrate] [AR Test] [Issues]      │  ← Top-center FABs
│                                         │
│                                         │
│                                         │
│                  [Dark Mode]            │  ← Right-side controls
│                  [Camera]               │
│                                         │
│                                         │
│                                         │
│                                         │
│            [Error Overlays]             │  ← Bottom (if errors)
└─────────────────────────────────────────┘
```

#### UI Components

| Component | Type | Description |
|-----------|------|-------------|
| **CameraPreview** | Custom Composable | CameraX preview for live feed |
| **SkyCanvas** | Custom Composable | Canvas drawing stars, Sun, Moon, constellations |
| **Calibration FAB** | FloatingActionButton | Opens calibration dialog |
| **AR Test FAB** | FloatingActionButton | Opens AR alignment guide |
| **Compass Issues FAB** | FloatingActionButton | Opens compass troubleshooting |
| **Dark Mode Toggle** | IconButton + Info | Switches background mode |
| **Screenshot Button** | IconButton + Info | Captures current view |
| **Error Overlay** | Surface | Displays recoverable errors |

#### Dialogs

| Dialog | Trigger | Content |
|--------|---------|---------|
| **PermissionDialog** | Missing permissions | Explains camera + location usage |
| **CalibrationDialog** | Calibrate FAB / first launch | Figure-8 calibration instructions |
| **ARTestDialog** | AR Test FAB | Step-by-step alignment verification |
| **CompassIssuesDialog** | Compass Issues FAB | Magnetic interference explanations |

---

### 2. Settings (Future/Planned)

Currently, settings are minimal and embedded in the main UI. Future iterations may include:

| Setting | Description |
|---------|-------------|
| **Star Magnitude Limit** | Filter stars by brightness threshold |
| **Constellation Toggle** | Show/hide constellation lines |
| **Label Density** | Control number of displayed labels |
| **Notification Settings** | Celestial event alerts |
| **About / Credits** | App version, library acknowledgments |

---

## Architecture

### Module Structure

```
Star Compass/
├── app/           # Main application module (UI, sensors, permissions)
├── core/          # Astronomy calculations, utilities
└── data/          # Room database, entities, repository
```

### Key Classes

| Module | Class | Responsibility |
|--------|-------|----------------|
| **app** | `MainActivity` | UI composition, permission handling, dialogs |
| **app** | `OrientationManager` | Sensor fusion, rotation matrix calculation |
| **app** | `LocationManager` | GPS location updates via Play Services |
| **app** | `SkyCanvas` | Celestial rendering on Canvas |
| **app** | `CameraPreview` | CameraX integration |
| **core** | `AstronomyUtils` | RA/Dec to Alt/Az conversion, LST, Sun/Moon positions |
| **data** | `Star` | Room entity for star data |
| **data** | `StarDatabase` | Room database with pre-populated stars |
| **data** | `StarRepository` | Data access abstraction |

---

## Libraries & Dependencies

### Official Android Libraries

| Library | Purpose |
|---------|---------|
| **AndroidX Core KTX** | Kotlin extensions for Android APIs |
| **AndroidX Compose BOM** | Jetpack Compose UI toolkit |
| **AndroidX Material3** | Material Design 3 components |
| **AndroidX CameraX** | Camera abstraction for preview |
| **AndroidX Room** | Local database for star catalog |
| **Play Services Location** | Fused location provider (GPS) |

### Kotlin Libraries

| Library | Purpose |
|---------|---------|
| **Kotlin Coroutines** | Async operations, Flow |
| **KotlinX Coroutines** | Structured concurrency |
| **KSP (Kotlin Symbol Processing)** | Room annotation processing |

### Astronomy Data Sources

| Source | Description |
|--------|-------------|
| **Custom Star Catalog** | Pre-seeded Room database with bright stars (magnitude < 6) |
| **VSX / Hipparcos-derived** | Star positions based on standard astronomical catalogs |
| **Low-precision Solar/Lunar Ephemeris** | Approximate Sun/Moon positions (accurate to ~1°) |

> **Note:** The app currently uses a simplified astronomical model suitable for visual AR purposes. For scientific-grade precision, integration with **JPL Horizons** or **VSOP87** ephemerides would be required.

---

## Technical Specifications

### Minimum Requirements

| Specification | Value |
|---------------|-------|
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 36 |
| **Compile SDK** | 36 |
| **Required Sensors** | Geomagnetic rotation vector, Accelerometer |
| **Required Permissions** | CAMERA, ACCESS_FINE_LOCATION, VIBRATE |

### Coordinate Systems

| System | Description |
|--------|-------------|
| **Equatorial (RA/Dec)** | Right Ascension (hours), Declination (degrees) — J2000 epoch |
| **Horizon (Alt/Az)** | Altitude (degrees), Azimuth (degrees from North) |
| **Screen** | Pixel coordinates with perspective projection |

### Projection Model

- **Field of View (Vertical):** 60°
- **Projection Scale Factor:** 50f
- **Perspective:** Central projection with Z-buffer culling (objects behind viewer hidden)

---

## Future Enhancements

| Feature | Priority | Description |
|---------|----------|-------------|
| **Planet Support** | High | Add Mercury, Venus, Mars, Jupiter, Saturn |
| **Deep Sky Objects** | Medium | Messier catalog integration |
| **Time Travel** | Medium | Slide to view sky at different dates/times |
| **Star Magnitude Filter** | Low | User-configurable brightness threshold |
| **Export/Share** | Low | Share screenshots with social media |
| **Night Mode (Red)** | Low | Preserve night vision with red-tinted UI |
| **Multi-language Support** | Medium | Localize constellation/star names |

---

## Known Limitations

| Limitation | Impact |
|------------|--------|
| **Indoor Compass Accuracy** | Magnetic interference from buildings affects orientation |
| **No True AR Depth** | Stars rendered at fixed distance (no parallax) |
| **Simplified Ephemeris** | Sun/Moon positions accurate to ~1°, not suitable for navigation |
| **Battery Consumption** | Continuous sensor + camera usage drains battery |

---

## Credits

- **Astronomical Algorithms:** Based on simplified formulas from *Astronomical Algorithms* by Jean Meeus
- **Star Names:** IAU-approved common names + traditional Arabic/Greek/Latin names
- **Constellation Patterns:** Standard IAU 88 constellations

---

*Last Updated: March 15, 2026*
