[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/4-04QCSZ)

# WiFinder

WiFinder is an Android app for indoor positioning at the University of Auckland.  
The current dataset covers parts of **Building 302** on floors **G (0)**, **1**, and **2**.

**Floor coverage (data collection areas)**
- Floor G (0): [Floor 0 Data](WiFi-Scan-Data-Images/Floor-0.png)
- Floor 1: [Floor 1 Data](WiFi-Scan-Data-Images/Floor-1.png)
- Floor 2: [Floor 2 Data](WiFi-Scan-Data-Images/Floor-2.png)

---

## What’s in this branch (stabilised pipeline)

This branch extends the baseline with a lightweight, on-device stabilisation stack:

- **AP selection & vectorisation** — [`utils/BssidVectorizer.kt`](app/src/main/java/com/example/compsci399testproject/utils/BssidVectorizer.kt)  
  Loads a whitelist from `assets/bssid_whitelist_order.txt` (fixed order). Only whitelisted BSSIDs are mapped to a fixed-size RSSI vector (RSSI clipped to −100…−20 dBm; keep max if duplicates). Missing entries default to −100 dBm.

- **Position smoothing** — [`utils/PositionSmoother.kt`](app/src/main/java/com/example/compsci399testproject/utils/PositionSmoother.kt)  
  Exponential Moving Average (EMA) on (x, y), α = 0.3, to reduce short-term jitter.

- **Coordinate handling** — [`utils/CoordTransform.kt`](app/src/main/java/com/example/compsci399testproject/utils/CoordTransform.kt)  
  Consistent origin/scale/Y-axis; helpers to convert to pixels for map rendering.

- **Robust Wi-Fi scanning** — [`utils/WifiScanner.kt`](app/src/main/java/com/example/compsci399testproject/utils/WifiScanner.kt)  
  Single in-flight scan, **≥ 8 s** trigger interval, **8 s** timeout, exponential backoff, cache fallback; permission/location checks; reliability stats posted to the ViewModel.

If you change the UI tick (e.g., `_wifiScanRate` in [`viewmodel/MapViewModel.kt`](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/blob/main/app/src/main/java/com/example/compsci399testproject/viewmodel/MapViewModel.kt)):

> It **cannot exceed** the scanner’s minimum interval in `WifiScanner.kt` (`minScanIntervalMs = 8000`).  
> Effective cadence is typically **~9 s/sample** (unless OS throttling applies).

---

## Requirements

- Android Studio (latest) with **Android Gradle Plugin ≥ 8.9.0**
- Android device or emulator (real device recommended)
- Enable **Wi-Fi** and **Location**; grant runtime permissions:
    - `ACCESS_FINE_LOCATION`
    - `NEARBY_WIFI_DEVICES` (Android 13+)

> **Wi-Fi scan throttling**: Some stock Android devices throttle scans in the background.  
> For faster testing, you may disable *Wi-Fi scan throttling* in **Developer options** (device-dependent).  
> HarmonyOS devices may behave differently.

---

## Build & Run

1. **Clone** this repo and open it in **Android Studio**.
2. When prompted, **Sync Gradle**.
3. Connect a device (or start an emulator) and **Run** the `app` module.

For general device/emulator setup, see the Android Studio docs.

---

## Update cadence knobs

- UI tick: `_wifiScanRate` in `viewmodel/MapViewModel.kt`.
- Scanner minimum interval: `minScanIntervalMs` in `utils/WifiScanner.kt` (default **8000 ms**).
- Per-scan timeout: `scanTimeoutMs` in `utils/WifiScanner.kt` (default **8000 ms**).

> The **largest** of these (plus any OS throttling) limits the effective update rate.

---

## Builds of the application

Development builds are available **[here](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/releases/tag/COMPSCI-399-Final)** (Android only).

---

## Future work (not specific to CS742)

- Client–server architecture for richer models and live model updates
- Improved data collection (robotic scanning, better tools)
- Integration with the official UoA Maps API for dynamic floor/room data
