package com.echomob.wifi

import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

object NetworkUtils {

  // ─── Scan helpers ──────────────────────────────────────────────────────────

  /**
   * Maps a [ScanResult] to a React-Native-compatible [WritableMap].
   *
   * The returned map includes an [isCached] flag to indicate whether the result
   * comes from a throttled/cached scan (Android 9+) rather than a fresh one.
   */
  fun scanResultToMap(result: ScanResult, isCached: Boolean = false, timestamp: Long = 0L): WritableMap {
    val map = Arguments.createMap()
    map.putString("ssid", result.SSID)
    map.putString("bssid", result.BSSID)
    map.putInt("signalLevel", result.level)
    map.putInt("frequency", result.frequency)
    map.putString("security", parseSecurityType(result.capabilities))
    // Scan metadata
    map.putBoolean("isCached", isCached)
    map.putDouble("timestamp", (if (timestamp > 0L) timestamp else System.currentTimeMillis()).toDouble())
    return map
  }

  /**
   * Parses the capabilities string of a [ScanResult] to a human-readable security type.
   */
  fun parseSecurityType(capabilities: String): String {
    return when {
      capabilities.contains("WPA3") -> "WPA3"
      capabilities.contains("WPA2") -> "WPA2"
      capabilities.contains("WPA")  -> "WPA"
      capabilities.contains("WEP")  -> "WEP"
      else                           -> "OPEN"
    }
  }

  // ─── Connect helpers ───────────────────────────────────────────────────────

  /**
   * Connects to a Wi-Fi network using [WifiNetworkSuggestion] on API 29+ (Android 10+)
   * or falls back to the legacy [WifiConfiguration] API for older versions.
   *
   * ### Android 10+ (API 29+) — WifiNetworkSuggestion (PERSISTENT)
   *   The app submits a network suggestion to the OS. Android stores it and
   *   automatically connects whenever the SSID is in range, even after reboot.
   *   This is the only API that provides a **persistent**, **non-transient** connection.
   *   ⚠️ The OS decides WHEN to connect — it is not instantaneous. The success callback
   *   fires once the suggestion is accepted, NOT once the device is connected.
   *
   * ### Android < 10 (API < 29) — Legacy WifiConfiguration
   *   Directly enables the network via the deprecated WifiManager API.
   *   The connection is immediate but [WifiConfiguration] is removed in API 33.
   *
   * @param wifiManager System [WifiManager].
   * @param ssid        SSID of the target network.
   * @param password    WPA2 passphrase, or null for open networks.
   * @param hidden      Whether the SSID is hidden.
   * @param onSuccess   Invoked when suggestion is accepted (API 29+) or network enabled (legacy).
   * @param onError     Invoked with [WifiError] on failure.
   */
  fun connectToNetwork(
    wifiManager: WifiManager,
    ssid: String,
    password: String?,
    hidden: Boolean,
    onSuccess: () -> Unit,
    onError: (WifiError) -> Unit
  ) {
    if (ssid.isBlank()) {
      onError(WifiError.INVALID_SSID)
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      connectWithSuggestion(wifiManager, ssid, password, hidden, onSuccess, onError)
    } else {
      connectLegacy(wifiManager, ssid, password, hidden, onSuccess, onError)
    }
  }

  /**
   * Android 10+ (API 29+): persistent connection via [WifiNetworkSuggestion].
   *
   * Removes any previous suggestion for the same SSID before adding a new one
   * to avoid STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE.
   *
   * Status codes returned by [WifiManager.addNetworkSuggestions]:
   *   - 0  → STATUS_NETWORK_SUGGESTIONS_SUCCESS
   *   - 1  → STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL
   *   - 2  → STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED
   *   - 3  → STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE  (handled: we remove first)
   *   - 4  → STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP
   */
  private fun connectWithSuggestion(
    wifiManager: WifiManager,
    ssid: String,
    password: String?,
    hidden: Boolean,
    onSuccess: () -> Unit,
    onError: (WifiError) -> Unit
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val suggestionBuilder = WifiNetworkSuggestion.Builder()
      .setSsid(ssid)
      .setIsHiddenSsid(hidden)

    if (!password.isNullOrEmpty()) {
      suggestionBuilder.setWpa2Passphrase(password)
    }

    val suggestion = suggestionBuilder.build()

    // Remove existing suggestion for this SSID to avoid duplicate errors.
    @Suppress("DEPRECATION")
    val existing = wifiManager.networkSuggestions
      .filter { it.ssid == ssid }
    if (existing.isNotEmpty()) {
      wifiManager.removeNetworkSuggestions(existing)
    }

    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

    when (status) {
      WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> onSuccess()
      else -> onError(WifiError.SUGGESTION_FAILED)
    }
  }

  /**
   * Android < 10: immediate connection via the legacy [WifiConfiguration] API.
   * [WifiConfiguration] is deprecated in API 29 and will not compile on API 33+,
   * so this block is only reached on older devices.
   */
  @Suppress("DEPRECATION")
  private fun connectLegacy(
    wifiManager: WifiManager,
    ssid: String,
    password: String?,
    hidden: Boolean,
    onSuccess: () -> Unit,
    onError: (WifiError) -> Unit
  ) {
    val wifiConfig = WifiConfiguration().apply {
      SSID = "\"$ssid\""
      if (password.isNullOrEmpty()) {
        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
      } else {
        preSharedKey = "\"$password\""
      }
      hiddenSSID = hidden
    }

    val netId = wifiManager.addNetwork(wifiConfig)
    if (netId == -1) {
      onError(WifiError.CONNECT_FAILED)
      return
    }
    wifiManager.disconnect()
    val enabled = wifiManager.enableNetwork(netId, true)
    wifiManager.reconnect()
    if (enabled) onSuccess() else onError(WifiError.CONNECT_FAILED)
  }
}
