/**
 * Wi‑Fi native module interface for react-native-echo-mob.
 * Provides methods to scan available Wi‑Fi networks and connect to a network.
 *
 * ## Android behaviour
 * - **Scan throttle** (Android 9+ / API 28+): `scanWifiNetworks` may return cached
 *   results when the OS throttle limit is reached. Check `isCached` and `timestamp`
 *   on each result to determine freshness.
 * - **Connection** (Android 10+ / API 29+): uses `WifiNetworkSuggestion` which is
 *   **persistent** (survives reboot) but **not immediate**. The OS decides when to
 *   connect. `connectToWifi` resolves when the suggestion is accepted.
 * - **Permissions**: each method auto-requests required permissions before executing.
 *   You can also call `requestPermissions()` explicitly during onboarding.
 *
 * ## iOS behaviour
 * - Full Wi-Fi scan is not permitted for public apps. Only the currently connected
 *   SSID is returned via `scanWifiNetworks` (isCached: false, single item).
 * - Connection uses `NEHotspotConfiguration`.
 */
import { NativeModules } from 'react-native';

// ─── Types ───────────────────────────────────────────────────────────────────

export type WifiSecurity = 'OPEN' | 'WEP' | 'WPA' | 'WPA2' | 'WPA3';

export type PermissionStatus = 'granted' | 'denied' | 'never_ask_again';

export interface WifiNetwork {
  /** The SSID (network name) */
  ssid: string;
  /** The BSSID (access point MAC address) */
  bssid: string;
  /** Signal strength in dBm (negative value; closer to 0 = stronger) */
  signalLevel: number;
  /** Frequency in MHz (2.4 GHz ≈ 2400, 5 GHz ≈ 5000) */
  frequency: number;
  /** Security type of the network */
  security: WifiSecurity;
  /**
   * Android only. True when the result comes from the OS cache due to scan throttling
   * (Android 9+). Results can be stale in this case; check `timestamp`.
   */
  isCached: boolean;
  /**
   * Unix timestamp (ms) of when the scan was initiated.
   * Use this to decide whether to trigger a fresh scan.
   */
  timestamp: number;
}

export interface ConnectOptions {
  /** Set to true if the network is hidden (SSID not broadcast). */
  hidden?: boolean;
}

// ─── Module ──────────────────────────────────────────────────────────────────

const native = NativeModules.WifiModule as {
  requestPermissions(): Promise<PermissionStatus>;
  scanWifiNetworks(): Promise<WifiNetwork[]>;
  connectToWifi(
    ssid: string,
    password: string | null,
    options: ConnectOptions | null
  ): Promise<void>;
  checkWifiEnabled(): Promise<boolean>;
};

export const WifiModule = {
  /**
   * Explicitly request all required Wi‑Fi + Location permissions.
   *
   * Each Wi‑Fi method also auto-requests permissions when needed, so calling
   * this explicitly is **optional** — useful for onboarding flows.
   *
   * @returns "granted" | "denied" | "never_ask_again"
   */
  requestPermissions(): Promise<PermissionStatus> {
    return native.requestPermissions();
  },

  /**
   * Scan for available Wi‑Fi networks.
   *
   * - **Android**: Returns all visible access points. If the OS scan is throttled
   *   (Android 9+), cached results are returned with `isCached: true`.
   *   Auto-requests permissions if missing.
   * - **iOS**: Returns only the currently connected network (single item), due to
   *   Apple platform restrictions.
   *
   * @throws {WifiError} PERMISSION_DENIED | WIFI_DISABLED | SCAN_FAILED
   */
  scanWifiNetworks(): Promise<WifiNetwork[]> {
    return native.scanWifiNetworks();
  },

  /**
   * Connect to a Wi‑Fi network.
   *
   * - **Android 10+**: Uses `WifiNetworkSuggestion` — connection is **persistent**
   *   but **asynchronous**. The OS connects when the SSID is in range.
   *   Resolves when the suggestion is accepted, not when the device is connected.
   * - **Android < 10**: Uses legacy `WifiConfiguration` — immediate connection.
   * - **iOS**: Uses `NEHotspotConfiguration`.
   *   Auto-requests permissions if missing.
   *
   * @param ssid     Target network SSID.
   * @param password WPA/WPA2 passphrase, or undefined for open networks.
   * @param options  `{ hidden?: boolean }`
   * @throws {WifiError} PERMISSION_DENIED | WIFI_DISABLED | CONNECT_FAILED | SUGGESTION_FAILED | INVALID_SSID
   */
  connectToWifi(
    ssid: string,
    password?: string,
    options?: ConnectOptions
  ): Promise<void> {
    return native.connectToWifi(ssid, password ?? null, options ?? null);
  },

  /**
   * Check whether Wi‑Fi is currently enabled on the device.
   *
   * @returns true if Wi‑Fi is enabled, false otherwise.
   */
  checkWifiEnabled(): Promise<boolean> {
    return native.checkWifiEnabled();
  },
};
