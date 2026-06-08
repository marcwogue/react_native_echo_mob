package com.echomob

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import com.echomob.wifi.WifiError
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
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
 * Unit tests for [WifiModule].
 *
 * Tests the orchestration layer (permission checks, Wi-Fi enabled check,
 * delegation to NetworkUtils) using mocked system services.
 *
 * Note: BroadcastReceiver scan flow is covered by integration/instrumentation tests.
 * Here we focus on the synchronous guard paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WifiModuleTest {

  @Mock private lateinit var mockContext: ReactApplicationContext
  @Mock private lateinit var mockWifiManager: WifiManager
  @Mock private lateinit var mockPromise: Promise

  private lateinit var module: WifiModule

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)

    // Wire the context to return our mock WifiManager.
    `when`(mockContext.applicationContext).thenReturn(mockContext)
    `when`(mockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mockWifiManager)

    module = WifiModule(mockContext)
  }

  // ─── checkWifiEnabled ─────────────────────────────────────────────────────

  @Test
  fun `checkWifiEnabled resolves true when Wi-Fi is on`() {
    `when`(mockWifiManager.isWifiEnabled).thenReturn(true)
    module.checkWifiEnabled(mockPromise)
    verify(mockPromise).resolve(true)
    verifyNoMoreInteractions(mockPromise)
  }

  @Test
  fun `checkWifiEnabled resolves false when Wi-Fi is off`() {
    `when`(mockWifiManager.isWifiEnabled).thenReturn(false)
    module.checkWifiEnabled(mockPromise)
    verify(mockPromise).resolve(false)
  }

  // ─── scanWifiNetworks – permission guard ──────────────────────────────────

  @Test
  fun `scanWifiNetworks auto-requests permissions when missing and Wi-Fi is disabled`() {
    // All permissions denied → ensurePermissionsThen should invoke system dialog.
    // Since we have no activity here, the fallback rejects the promise.
    `when`(
      mockContext.checkPermission(
        eq(Manifest.permission.ACCESS_FINE_LOCATION), anyInt(), anyInt()
      )
    ).thenReturn(PackageManager.PERMISSION_DENIED)

    // No current activity → auto-request path falls back to reject.
    module.scanWifiNetworks(mockPromise)

    verify(mockPromise).reject(
      eq(WifiError.PERMISSION_DENIED.code),
      contains("No active Activity")
    )
  }

  @Test
  fun `scanWifiNetworks rejects with WIFI_DISABLED when Wi-Fi is off and permissions granted`() {
    // Grant all permissions.
    grantAllPermissions()
    `when`(mockWifiManager.isWifiEnabled).thenReturn(false)

    module.scanWifiNetworks(mockPromise)

    verify(mockPromise).reject(
      eq(WifiError.WIFI_DISABLED.code),
      eq(WifiError.WIFI_DISABLED.message)
    )
  }

  // ─── connectToWifi – permission guard ─────────────────────────────────────

  @Test
  fun `connectToWifi auto-requests permissions when missing`() {
    // No activity → auto-request rejects.
    `when`(
      mockContext.checkPermission(
        eq(Manifest.permission.ACCESS_FINE_LOCATION), anyInt(), anyInt()
      )
    ).thenReturn(PackageManager.PERMISSION_DENIED)

    module.connectToWifi("Net", "pass", null, mockPromise)

    verify(mockPromise).reject(
      eq(WifiError.PERMISSION_DENIED.code),
      contains("No active Activity")
    )
  }

  @Test
  fun `connectToWifi rejects with WIFI_DISABLED when Wi-Fi is off and permissions granted`() {
    grantAllPermissions()
    `when`(mockWifiManager.isWifiEnabled).thenReturn(false)

    module.connectToWifi("Net", "pass", null, mockPromise)

    verify(mockPromise).reject(
      eq(WifiError.WIFI_DISABLED.code),
      eq(WifiError.WIFI_DISABLED.message)
    )
  }

  // ─── WifiError enum sanity checks ─────────────────────────────────────────

  @Test
  fun `WifiError codes are stable strings`() {
    assertEquals("PERMISSION_DENIED",  WifiError.PERMISSION_DENIED.code)
    assertEquals("WIFI_DISABLED",      WifiError.WIFI_DISABLED.code)
    assertEquals("SCAN_FAILED",        WifiError.SCAN_FAILED.code)
    assertEquals("CONNECT_FAILED",     WifiError.CONNECT_FAILED.code)
    assertEquals("SUGGESTION_FAILED",  WifiError.SUGGESTION_FAILED.code)
    assertEquals("ALREADY_CONNECTED",  WifiError.ALREADY_CONNECTED.code)
    assertEquals("INVALID_SSID",       WifiError.INVALID_SSID.code)
    assertEquals("UNKNOWN",            WifiError.UNKNOWN.code)
  }

  @Test
  fun `WifiError messages are non-blank`() {
    WifiError.values().forEach { error ->
      assertTrue(
        "WifiError.${error.name}.message must not be blank",
        error.message.isNotBlank()
      )
    }
  }

  // ─── Helper ───────────────────────────────────────────────────────────────

  /** Grant all Wi-Fi + location permissions on the mock context. */
  private fun grantAllPermissions() {
    listOf(
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_WIFI_STATE,
      Manifest.permission.CHANGE_WIFI_STATE,
    ).forEach { perm ->
      `when`(mockContext.checkPermission(eq(perm), anyInt(), anyInt()))
        .thenReturn(PackageManager.PERMISSION_GRANTED)
    }
  }
}
