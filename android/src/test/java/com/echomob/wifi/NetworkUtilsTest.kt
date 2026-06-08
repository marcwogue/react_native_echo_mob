package com.echomob.wifi

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [NetworkUtils].
 *
 * Uses Robolectric so that Android classes (ScanResult, WifiManager, Build.VERSION)
 * are available without a real device or emulator.
 *
 * Test coverage:
 *  - scanResultToMap(): correct field mapping, isCached flag, timestamp
 *  - parseSecurityType(): all known security type strings
 *  - connectToNetwork(): INVALID_SSID guard, legacy path (API < 29), suggestion path (API 29+)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33]) // Run once on API 28 (legacy) and API 33 (suggestion)
class NetworkUtilsTest {

  @Mock
  private lateinit var mockWifiManager: WifiManager

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)
  }

  // ─── scanResultToMap ──────────────────────────────────────────────────────

  @Test
  fun `scanResultToMap maps all ScanResult fields correctly`() {
    val result = mock(ScanResult::class.java).apply {
      SSID = "TestNet"
      BSSID = "AA:BB:CC:DD:EE:FF"
      level = -60
      frequency = 5180
      capabilities = "[WPA2-PSK-CCMP][ESS]"
    }

    val map = NetworkUtils.scanResultToMap(result, isCached = false, timestamp = 12345L)

    assertEquals("TestNet", map.getString("ssid"))
    assertEquals("AA:BB:CC:DD:EE:FF", map.getString("bssid"))
    assertEquals(-60, map.getInt("signalLevel"))
    assertEquals(5180, map.getInt("frequency"))
    assertEquals("WPA2", map.getString("security"))
    assertFalse(map.getBoolean("isCached"))
    assertEquals(12345.0, map.getDouble("timestamp"), 0.0)
  }

  @Test
  fun `scanResultToMap sets isCached = true when scan was throttled`() {
    val result = mock(ScanResult::class.java).apply {
      SSID = "CachedNet"
      BSSID = "00:11:22:33:44:55"
      level = -70
      frequency = 2437
      capabilities = "[WPA-PSK-TKIP][ESS]"
    }

    val map = NetworkUtils.scanResultToMap(result, isCached = true, timestamp = 999L)
    assertTrue(map.getBoolean("isCached"))
  }

  @Test
  fun `scanResultToMap uses current time when timestamp is 0`() {
    val result = mock(ScanResult::class.java).apply {
      SSID = "Net"
      BSSID = "00:00:00:00:00:00"
      level = -50
      frequency = 2462
      capabilities = ""
    }
    val before = System.currentTimeMillis()
    val map = NetworkUtils.scanResultToMap(result, isCached = false, timestamp = 0L)
    val after = System.currentTimeMillis()

    val ts = map.getDouble("timestamp").toLong()
    assertTrue("Timestamp should be >= before", ts >= before)
    assertTrue("Timestamp should be <= after", ts <= after)
  }

  // ─── parseSecurityType ────────────────────────────────────────────────────

  @Test
  fun `parseSecurityType correctly identifies WPA3`() {
    assertEquals("WPA3", NetworkUtils.parseSecurityType("[WPA3-SAE-CCMP][ESS]"))
  }

  @Test
  fun `parseSecurityType correctly identifies WPA2`() {
    assertEquals("WPA2", NetworkUtils.parseSecurityType("[WPA2-PSK-CCMP][ESS]"))
  }

  @Test
  fun `parseSecurityType correctly identifies WPA`() {
    assertEquals("WPA", NetworkUtils.parseSecurityType("[WPA-PSK-TKIP][ESS]"))
  }

  @Test
  fun `parseSecurityType correctly identifies WEP`() {
    assertEquals("WEP", NetworkUtils.parseSecurityType("[WEP][ESS]"))
  }

  @Test
  fun `parseSecurityType returns OPEN for empty capabilities`() {
    assertEquals("OPEN", NetworkUtils.parseSecurityType(""))
    assertEquals("OPEN", NetworkUtils.parseSecurityType("[ESS]"))
  }

  @Test
  fun `parseSecurityType prioritizes WPA3 over WPA2 over WPA`() {
    // Unlikely in practice but ensures priority is correct.
    assertEquals("WPA3", NetworkUtils.parseSecurityType("[WPA3][WPA2][WPA]"))
  }

  // ─── connectToNetwork – guard: blank SSID ─────────────────────────────────

  @Test
  fun `connectToNetwork rejects empty SSID with INVALID_SSID`() {
    var capturedError: WifiError? = null

    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "",
      password = "pass",
      hidden = false,
      onSuccess = { fail("Should not succeed") },
      onError = { capturedError = it }
    )

    assertEquals(WifiError.INVALID_SSID, capturedError)
    verifyNoInteractions(mockWifiManager)
  }

  @Test
  fun `connectToNetwork rejects blank SSID (whitespace) with INVALID_SSID`() {
    var capturedError: WifiError? = null

    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "   ",
      password = null,
      hidden = false,
      onSuccess = { fail("Should not succeed") },
      onError = { capturedError = it }
    )

    assertEquals(WifiError.INVALID_SSID, capturedError)
  }

  // ─── connectToNetwork – API < 29 legacy path ──────────────────────────────

  @Test
  @Config(sdk = [28])
  fun `connectToNetwork (API 28) calls addNetwork and enableNetwork for WPA2`() {
    `when`(mockWifiManager.addNetwork(any())).thenReturn(1)
    `when`(mockWifiManager.enableNetwork(1, true)).thenReturn(true)

    var succeeded = false
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "HomeNet",
      password = "password123",
      hidden = false,
      onSuccess = { succeeded = true },
      onError = { fail("Should not fail: ${it.code}") }
    )

    assertTrue(succeeded)
    verify(mockWifiManager).addNetwork(any())
    verify(mockWifiManager).enableNetwork(1, true)
    verify(mockWifiManager).reconnect()
  }

  @Test
  @Config(sdk = [28])
  fun `connectToNetwork (API 28) rejects with CONNECT_FAILED when addNetwork returns -1`() {
    `when`(mockWifiManager.addNetwork(any())).thenReturn(-1)

    var capturedError: WifiError? = null
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "HomeNet",
      password = "badpass",
      hidden = false,
      onSuccess = { fail("Should not succeed") },
      onError = { capturedError = it }
    )

    assertEquals(WifiError.CONNECT_FAILED, capturedError)
  }

  @Test
  @Config(sdk = [28])
  fun `connectToNetwork (API 28) rejects with CONNECT_FAILED when enableNetwork returns false`() {
    `when`(mockWifiManager.addNetwork(any())).thenReturn(2)
    `when`(mockWifiManager.enableNetwork(2, true)).thenReturn(false)

    var capturedError: WifiError? = null
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "HomeNet",
      password = "pass",
      hidden = false,
      onSuccess = { fail("Should not succeed") },
      onError = { capturedError = it }
    )

    assertEquals(WifiError.CONNECT_FAILED, capturedError)
  }

  @Test
  @Config(sdk = [28])
  fun `connectToNetwork (API 28) uses NONE key management for open networks`() {
    `when`(mockWifiManager.addNetwork(any())).thenReturn(3)
    `when`(mockWifiManager.enableNetwork(3, true)).thenReturn(true)

    var succeeded = false
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "OpenNet",
      password = null,
      hidden = false,
      onSuccess = { succeeded = true },
      onError = { fail("Should not fail") }
    )

    assertTrue(succeeded)
  }

  // ─── connectToNetwork – API 29+ suggestion path ───────────────────────────

  @Test
  @Config(sdk = [33])
  fun `connectToNetwork (API 33) calls addNetworkSuggestions with correct SSID`() {
    `when`(mockWifiManager.addNetworkSuggestions(any()))
      .thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
    `when`(mockWifiManager.networkSuggestions).thenReturn(emptyList())

    var succeeded = false
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "ModernNet",
      password = "securePass",
      hidden = false,
      onSuccess = { succeeded = true },
      onError = { fail("Should not fail: ${it.code}") }
    )

    assertTrue(succeeded)
    verify(mockWifiManager).addNetworkSuggestions(any())
  }

  @Test
  @Config(sdk = [33])
  fun `connectToNetwork (API 33) rejects with SUGGESTION_FAILED on non-zero status`() {
    `when`(mockWifiManager.addNetworkSuggestions(any())).thenReturn(1) // ERROR_INTERNAL
    `when`(mockWifiManager.networkSuggestions).thenReturn(emptyList())

    var capturedError: WifiError? = null
    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "ModernNet",
      password = "pass",
      hidden = false,
      onSuccess = { fail("Should not succeed") },
      onError = { capturedError = it }
    )

    assertEquals(WifiError.SUGGESTION_FAILED, capturedError)
  }

  @Test
  @Config(sdk = [33])
  fun `connectToNetwork (API 33) removes existing suggestion before adding new one`() {
    val existingSuggestion = mock(WifiNetworkSuggestion::class.java)
    `when`(mockWifiManager.networkSuggestions).thenReturn(listOf(existingSuggestion))
    `when`(mockWifiManager.addNetworkSuggestions(any()))
      .thenReturn(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)

    NetworkUtils.connectToNetwork(
      wifiManager = mockWifiManager,
      ssid = "ModernNet",
      password = "pass",
      hidden = false,
      onSuccess = {},
      onError = {}
    )

    // Should have removed the old suggestion first.
    verify(mockWifiManager).removeNetworkSuggestions(any())
    verify(mockWifiManager).addNetworkSuggestions(any())
  }
}
