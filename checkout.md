# Wi‑Fi Scanning & Connection Feature – Checkout & Roadmap

## Overview
We are extending the **react‑native‑echo‑mob** native module with Wi‑Fi capabilities:

1. **Scan available Wi‑Fi access points** – return a list of SSIDs (and basic metadata such as signal strength, security type).
2. **Connect to a Wi‑Fi network** – given an `ssid` and optional `password`, attempt to join the network (supporting open, WPA/WPA2, WPA3 where possible).
3. **Handle runtime permissions** on Android and iOS automatically.
4. **Expose a clean TypeScript bridge** so JavaScript/TS code can call the native functions and receive typed results.

The implementation will be split between **Android (Kotlin)**, **iOS (Objective‑C++/Swift)**, and the **React‑Native TypeScript bridge**.

---

## Functional Specification

| Feature | Description | Platform | Bridge API |
| ------- | ----------- | -------- | ---------- |
| `scanWifiNetworks()` | Returns an array of visible Wi‑Fi networks. Each entry includes `ssid`, `bssid`, `signalLevel`, `frequency`, `security`, plus **`isCached`** and **`timestamp`** (throttle metadata). | Android, iOS | `Promise<WifiNetwork[]>` |
| `connectToWifi(ssid, password?, options?)` | Connects to the specified network. Android 10+ uses `WifiNetworkSuggestion` (persistent, async). Android < 10 uses legacy `WifiConfiguration` (immediate). iOS uses `NEHotspotConfiguration`. | Android, iOS | `Promise<void>` |
| `checkWifiEnabled()` | Returns boolean indicating whether Wi‑Fi is enabled. | Android, iOS | `Promise<boolean>` |
| `requestPermissions()` | **Optional** explicit permission request. Each method auto-requests if needed. Useful for onboarding flows. | Android, iOS | `Promise<PermissionStatus>` |

---

## TypeScript Bridge API (src/index.ts)
```ts
export type WifiSecurity = 'OPEN' | 'WEP' | 'WPA' | 'WPA2' | 'WPA3';
export type PermissionStatus = 'granted' | 'denied' | 'never_ask_again';

export interface WifiNetwork {
  ssid: string;
  bssid: string;
  signalLevel: number; // dBm (negative; closer to 0 = stronger signal)
  frequency: number;   // MHz (≈2400 for 2.4 GHz, ≈5000 for 5 GHz)
  security: WifiSecurity;
  /** True when the OS returned a cached result due to scan throttling (Android 9+). */
  isCached: boolean;
  /** Unix timestamp (ms) of when the scan was initiated. */
  timestamp: number;
}

export interface ConnectOptions {
  /** Whether the network broadcasts its SSID. */
  hidden?: boolean;
}

export const WifiModule = {
  /** Optional explicit permission request (each method also auto-requests). */
  requestPermissions(): Promise<PermissionStatus>;
  /** Scan visible access points. May return cached results on Android 9+ (check isCached). */
  scanWifiNetworks(): Promise<WifiNetwork[]>;
  /** Connect to a network. Persistent on Android 10+ via WifiNetworkSuggestion. */
  connectToWifi(ssid: string, password?: string, options?: ConnectOptions): Promise<void>;
  checkWifiEnabled(): Promise<boolean>;
};
```

---

## Platform Limitations & Architectural Decisions

### Android — Scan Throttle (API 28+)
> Android 9+ limits `WifiManager.startScan()` to approximately **4 calls every 2 minutes** in the foreground and **1 call every 30 minutes** in the background.

**Decision**: When throttled, `scanWifiNetworks` returns cached results from `getScanResults()` with `isCached: true` and a `timestamp`. The promise never rejects due to throttling alone. Consumers should check `isCached` and retry after a delay if freshness matters.

### Android — Connection API (API 29+)
> `WifiNetworkSpecifier` creates a **temporary** connection that drops when the requesting app goes to the background or is killed.
>
> `WifiNetworkSuggestion` creates a **persistent** suggestion: the OS stores it, connects automatically when the SSID is in range, and it survives reboots.

**Decision**: `connectToWifi` uses `WifiNetworkSuggestion` on API 29+ for a persistent connection. The promise resolves when the suggestion is **accepted** by the OS, not when the device is actually connected. Consumers must not assume the device is online immediately after resolution.

### Android — Permissions (API 23+)
> Permissions must be granted before each scan or connection attempt. A silent failure occurs if they are missing.

**Decision**: Each method calls `ensurePermissionsThen()` internally, which auto-triggers the system permission dialog if any permission is missing. An explicit `requestPermissions()` method is also available for onboarding flows.

### iOS — Scan Restriction
> iOS does not allow full Wi-Fi scanning for public apps. `CNCopySupportedInterfaces` + `CNCopyCurrentNetworkInfo` only return the **currently connected** network.

**Decision**: `scanWifiNetworks()` on iOS returns a single-item array with the current SSID, or an empty array if not connected. The `isCached` field is always `false` on iOS.

---

## Implementation Roadmap

### Phase 1 – Research & Foundations 
- Verify minimum Android SDK (API 21) and iOS SDK (13) support for Wi‑Fi APIs.
- Choose native APIs:
  - Android: `WifiManager`, `WifiNetworkSuggestion`, `WifiNetworkSpecifier` (fallback to legacy scan if needed).
  - iOS: `NEHotspotConfigurationManager` for connection, `CNCopySupportedInterfaces` + `CNCopyCurrentNetworkInfo` for scanning (note iOS restricts scanning; we will use the *Network Extension* approach which requires the `Access Wi‑Fi Information` entitlement).
- Draft TypeScript typings (as above) and add placeholder methods in `src/index.ts`.

### Phase 2 – Android Native Module 
1. **Permissions** – Implement runtime request for `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CHANGE_WIFI_STATE`, `ACCESS_WIFI_STATE`.
2. **Scanning** – Use `WifiManager.startScan()` and `WifiManager.getScanResults()`; map `ScanResult` → `WifiNetwork`.
3. **Connection** – Implement using `WifiNetworkSuggestion` for modern Android (API 29+) and fallback to legacy `WifiConfiguration` for older versions.
4. **Bridge** – Add `EchoMobModule.kt` methods annotated with `@ReactMethod` to expose the four APIs.
5. **Error handling** – Define a small error enum (`SCAN_FAILED`, `CONNECT_FAILED`, `PERMISSION_DENIED`).
6. **Unit tests** – Write instrumentation tests using `androidx.test` to mock `WifiManager`.

### Phase 3 – iOS Native Module 
1. **Entitlements** – Add `com.apple.developer.networking.HotspotConfiguration` and `com.apple.developer.access-wifi-information` to the Xcode project.
2. **Permissions** – Request `locationWhenInUse` (required for Wi‑Fi scanning on iOS >= 13).
3. **Scanning** – Use `CNCopySupportedInterfaces` + `CNCopyCurrentNetworkInfo` to retrieve the *current* SSID. Full scan is not permitted on iOS without private APIs; we will expose the current network only and document the limitation. (If Apple later allows full scan, the module can be extended.)
4. **Connection** – Implement using `NEHotspotConfiguration` (supports WPA/WPA2/WPA3, open networks, hidden SSIDs). Handle success/failure callbacks.
5. **Bridge** – Add Objective‑C++ wrapper `EchoMob.mm` exposing methods via `RCT_EXPORT_METHOD`.
6. **Unit tests** – Use `XCTest` with mocks for `NEHotspotConfigurationManager`.

### Phase 4 – TypeScript Bridge & Documentation 
- Implement the JavaScript wrapper that forwards calls to native module via `NativeModules.EchoMob`.
- Add JSDoc comments and publish typings.
- Write usage README section and update `README.md`.

### Phase 5 – Integration & QA 
- Create a demo screen in the example app (`example/src/App.tsx`) showcasing scanning and connection flows.
- Perform manual testing on a range of Android devices (API 21‑33) and iOS devices (13‑17).
- Verify permission flows, error handling, and edge cases (e.g., wrong password, hidden SSID).
- Collect feedback and iterate.

---

## Milestones & Timeline 
| Milestone | Target Date | Deliverables |
| --------- | ----------- | ------------ |
| Research & Types | +1 wk | API docs, TypeScript stubs |
| Android Module | +3 wk | Kotlin implementation, unit tests |
| iOS Module | +5 wk | Objective‑C++ implementation, entitlements, unit tests |
| Bridge & Docs | +6 wk | JS wrapper, README updates |
| Demo & QA | +7 wk | Example app integration, bug‑free release |

---

## Open Questions / Decisions
> [!IMPORTANT]
> - **iOS scanning limitation** – iOS does not allow full Wi‑Fi scans for public apps. Do we accept “current network only” for iOS, or plan a future private‑API solution?
> - **Permission UX** – Should the native module automatically show system dialogs, or expose a separate `requestPermissions()` that the app can call at a convenient time?
> - **Error handling strategy** – Do we expose raw platform error codes, or map them to a unified error enum for the JS layer?

Please review the checklist above and confirm any preferences or adjustments. Once approved, we can start creating the native source files and TypeScript glue.
