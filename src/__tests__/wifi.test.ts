/**
 * @jest-environment node
 */
/**
 * Unit tests for the WifiModule TypeScript bridge.
 *
 * Strategy: jest.mock() is hoisted before all imports, so mock functions
 * must be created inside the factory. We expose them on a shared object
 * so test cases can configure them per-test.
 */
import { describe, it, expect, beforeEach, jest } from '@jest/globals';
import type { WifiNetwork, PermissionStatus } from '../wifi';

// Each mock is typed to match its native method signature so that
// mockResolvedValueOnce / mockRejectedValueOnce accept the correct value types.
const mocks = {
  requestPermissions: jest.fn() as jest.MockedFunction<
    () => Promise<PermissionStatus>
  >,
  scanWifiNetworks: jest.fn() as jest.MockedFunction<
    () => Promise<WifiNetwork[]>
  >,
  connectToWifi: jest.fn() as jest.MockedFunction<
    (
      ssid: string,
      password: string | null,
      options: Record<string, unknown> | null
    ) => Promise<void>
  >,
  checkWifiEnabled: jest.fn() as jest.MockedFunction<() => Promise<boolean>>,
};

// ─── Mock react-native before any import resolves ─────────────────────────────
jest.mock('react-native', () => ({
  NativeModules: {
    WifiModule: {
      requestPermissions: () => mocks.requestPermissions(),
      scanWifiNetworks: () => mocks.scanWifiNetworks(),
      connectToWifi: (
        ssid: string,
        password: string | null,
        options: Record<string, unknown> | null
      ) => mocks.connectToWifi(ssid, password, options),
      checkWifiEnabled: () => mocks.checkWifiEnabled(),
    },
  },
}));

// ─── Import module under test ─────────────────────────────────────────────────
import { WifiModule } from '../wifi';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeNetwork(overrides?: Partial<WifiNetwork>): WifiNetwork {
  return {
    ssid: 'TestNetwork',
    bssid: 'AA:BB:CC:DD:EE:FF',
    signalLevel: -55,
    frequency: 2437,
    security: 'WPA2',
    isCached: false,
    timestamp: Date.now(),
    ...overrides,
  };
}

beforeEach(() => {
  mocks.requestPermissions.mockReset();
  mocks.scanWifiNetworks.mockReset();
  mocks.connectToWifi.mockReset();
  mocks.checkWifiEnabled.mockReset();
});

// ── requestPermissions ────────────────────────────────────────────────────────

describe('WifiModule.requestPermissions()', () => {
  it('resolves with "granted"', async () => {
    mocks.requestPermissions.mockResolvedValueOnce('granted');
    const result: PermissionStatus = await WifiModule.requestPermissions();
    expect(result).toBe('granted');
    expect(mocks.requestPermissions).toHaveBeenCalledTimes(1);
  });

  it('resolves with "denied"', async () => {
    mocks.requestPermissions.mockResolvedValueOnce('denied');
    expect(await WifiModule.requestPermissions()).toBe('denied');
  });

  it('resolves with "never_ask_again"', async () => {
    mocks.requestPermissions.mockResolvedValueOnce('never_ask_again');
    expect(await WifiModule.requestPermissions()).toBe('never_ask_again');
  });

  it('rejects on native error', async () => {
    mocks.requestPermissions.mockRejectedValueOnce(
      new Error('PERMISSION_DENIED: No active Activity')
    );
    await expect(WifiModule.requestPermissions()).rejects.toThrow(
      'PERMISSION_DENIED'
    );
  });
});

// ── checkWifiEnabled ──────────────────────────────────────────────────────────

describe('WifiModule.checkWifiEnabled()', () => {
  it('resolves with true when Wi-Fi is enabled', async () => {
    mocks.checkWifiEnabled.mockResolvedValueOnce(true);
    expect(await WifiModule.checkWifiEnabled()).toBe(true);
    expect(mocks.checkWifiEnabled).toHaveBeenCalledTimes(1);
  });

  it('resolves with false when Wi-Fi is disabled', async () => {
    mocks.checkWifiEnabled.mockResolvedValueOnce(false);
    expect(await WifiModule.checkWifiEnabled()).toBe(false);
  });

  it('rejects on UNKNOWN native error', async () => {
    mocks.checkWifiEnabled.mockRejectedValueOnce(new Error('UNKNOWN'));
    await expect(WifiModule.checkWifiEnabled()).rejects.toThrow('UNKNOWN');
  });
});

// ── scanWifiNetworks ──────────────────────────────────────────────────────────

describe('WifiModule.scanWifiNetworks()', () => {
  it('resolves with a list of WifiNetwork objects', async () => {
    const networks: WifiNetwork[] = [
      makeNetwork({ ssid: 'HomeNetwork', security: 'WPA2' }),
      makeNetwork({ ssid: 'CafeWifi', security: 'OPEN', signalLevel: -80 }),
    ];
    mocks.scanWifiNetworks.mockResolvedValueOnce(networks);

    const result = await WifiModule.scanWifiNetworks();
    expect(result).toHaveLength(2);
    expect(result[0]!.ssid).toBe('HomeNetwork');
    expect(result[1]!.security).toBe('OPEN');
  });

  it('each result has isCached (boolean) and timestamp (number) fields', async () => {
    const ts = Date.now();
    mocks.scanWifiNetworks.mockResolvedValueOnce([
      makeNetwork({ isCached: false, timestamp: ts }),
    ]);

    const [first] = await WifiModule.scanWifiNetworks();
    expect(typeof first!.isCached).toBe('boolean');
    expect(typeof first!.timestamp).toBe('number');
  });

  it('isCached = true when OS returns throttled/cached results', async () => {
    mocks.scanWifiNetworks.mockResolvedValueOnce([
      makeNetwork({ isCached: true, timestamp: Date.now() - 60_000 }),
    ]);
    const [first] = await WifiModule.scanWifiNetworks();
    expect(first!.isCached).toBe(true);
  });

  it('resolves with empty array when no networks found', async () => {
    mocks.scanWifiNetworks.mockResolvedValueOnce([]);
    expect(await WifiModule.scanWifiNetworks()).toEqual([]);
  });

  it('rejects with PERMISSION_DENIED when permissions missing', async () => {
    mocks.scanWifiNetworks.mockRejectedValueOnce(
      new Error('PERMISSION_DENIED: Required permissions are not granted.')
    );
    await expect(WifiModule.scanWifiNetworks()).rejects.toThrow(
      'PERMISSION_DENIED'
    );
  });

  it('rejects with WIFI_DISABLED when Wi-Fi is off', async () => {
    mocks.scanWifiNetworks.mockRejectedValueOnce(
      new Error('WIFI_DISABLED: Wi-Fi is disabled on this device.')
    );
    await expect(WifiModule.scanWifiNetworks()).rejects.toThrow(
      'WIFI_DISABLED'
    );
  });

  it('rejects with SCAN_FAILED when throttled and no cache', async () => {
    mocks.scanWifiNetworks.mockRejectedValueOnce(
      new Error('SCAN_FAILED: Wi-Fi scan failed or returned no results.')
    );
    await expect(WifiModule.scanWifiNetworks()).rejects.toThrow('SCAN_FAILED');
  });
});

// ── connectToWifi ─────────────────────────────────────────────────────────────

describe('WifiModule.connectToWifi()', () => {
  it('resolves for a WPA2 network', async () => {
    mocks.connectToWifi.mockResolvedValueOnce(undefined);
    await expect(
      WifiModule.connectToWifi('HomeNetwork', 'SecurePass123')
    ).resolves.toBeUndefined();
    expect(mocks.connectToWifi).toHaveBeenCalledWith(
      'HomeNetwork',
      'SecurePass123',
      null
    );
  });

  it('resolves for an open network (no password)', async () => {
    mocks.connectToWifi.mockResolvedValueOnce(undefined);
    await expect(WifiModule.connectToWifi('OpenWifi')).resolves.toBeUndefined();
    expect(mocks.connectToWifi).toHaveBeenCalledWith('OpenWifi', null, null);
  });

  it('passes hidden option correctly', async () => {
    mocks.connectToWifi.mockResolvedValueOnce(undefined);
    await WifiModule.connectToWifi('HiddenNet', 'pass', { hidden: true });
    expect(mocks.connectToWifi).toHaveBeenCalledWith('HiddenNet', 'pass', {
      hidden: true,
    });
  });

  it('rejects with PERMISSION_DENIED when permissions missing', async () => {
    mocks.connectToWifi.mockRejectedValueOnce(
      new Error('PERMISSION_DENIED: Required permissions are not granted.')
    );
    await expect(WifiModule.connectToWifi('Net', 'pass')).rejects.toThrow(
      'PERMISSION_DENIED'
    );
  });

  it('rejects with WIFI_DISABLED when Wi-Fi is off', async () => {
    mocks.connectToWifi.mockRejectedValueOnce(
      new Error('WIFI_DISABLED: Wi-Fi is disabled on this device.')
    );
    await expect(WifiModule.connectToWifi('Net', 'pass')).rejects.toThrow(
      'WIFI_DISABLED'
    );
  });

  it('rejects with CONNECT_FAILED on legacy failure (API < 29)', async () => {
    mocks.connectToWifi.mockRejectedValueOnce(
      new Error('CONNECT_FAILED: Failed to connect to the Wi-Fi network.')
    );
    await expect(WifiModule.connectToWifi('Net', 'pass')).rejects.toThrow(
      'CONNECT_FAILED'
    );
  });

  it('rejects with SUGGESTION_FAILED when WifiNetworkSuggestion rejected (API 29+)', async () => {
    mocks.connectToWifi.mockRejectedValueOnce(
      new Error('SUGGESTION_FAILED: WifiNetworkSuggestion could not be added.')
    );
    await expect(WifiModule.connectToWifi('Net', 'pass')).rejects.toThrow(
      'SUGGESTION_FAILED'
    );
  });

  it('rejects with INVALID_SSID for empty SSID', async () => {
    mocks.connectToWifi.mockRejectedValueOnce(
      new Error('INVALID_SSID: The provided SSID is null or empty.')
    );
    await expect(WifiModule.connectToWifi('')).rejects.toThrow('INVALID_SSID');
  });
});
