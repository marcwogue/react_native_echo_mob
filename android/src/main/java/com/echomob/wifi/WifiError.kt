package com.echomob.wifi

/**
 * Unified error codes exposed to the JavaScript layer via Promise rejection.
 */
enum class WifiError(val code: String, val message: String) {
  PERMISSION_DENIED("PERMISSION_DENIED", "Required permissions are not granted."),
  WIFI_DISABLED("WIFI_DISABLED", "Wi-Fi is disabled on this device."),
  SCAN_FAILED("SCAN_FAILED", "Wi-Fi scan failed or returned no results."),
  CONNECT_FAILED("CONNECT_FAILED", "Failed to connect to the Wi-Fi network."),
  SUGGESTION_FAILED("SUGGESTION_FAILED", "WifiNetworkSuggestion could not be added."),
  ALREADY_CONNECTED("ALREADY_CONNECTED", "Already connected to this network."),
  INVALID_SSID("INVALID_SSID", "The provided SSID is null or empty."),
  UNKNOWN("UNKNOWN", "An unknown error occurred.");
}
