package com.echomob

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.echomob.wifi.NetworkUtils
import com.echomob.wifi.WifiError
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

/**
 * WifiModule exposes Wi-Fi scan and connection functionality to React Native.
 *
 * ## Permission Strategy
 * Permissions are checked before every sensitive operation. If permissions are
 * missing, the call automatically triggers the system permission dialog (via
 * [ensurePermissionsThen]) before continuing. An optional standalone
 * [requestPermissions] method is also exposed for explicit control.
 *
 * ## Android Version Behaviour
 * | API Level | Scan throttle         | Connection API         |
 * |-----------|----------------------|------------------------|
 * | < 29      | Not throttled        | WifiConfiguration (legacy) |
 * | 29+       | 4 scans / 2 min      | WifiNetworkSuggestion (persistent) |
 *
 * ## Scan Throttle (Android 9+ / API 28+)
 * `WifiManager.startScan()` is throttled to ~4 calls/2 min in the foreground and
 * 1 call/30 min in the background. When throttled, `startScan()` returns `false`
 * and `EXTRA_RESULTS_UPDATED` is `false`. In that case we return cached results
 * with `isCached = true` and a `timestamp` of the last successful scan.
 *
 * ## Connection (Android 10+ / API 29+)
 * Uses [WifiNetworkSuggestion] which registers a persistent suggestion with the OS.
 * The OS will connect automatically when the SSID is in range. This is NOT
 * immediate — success callback fires when the suggestion is accepted, not on
 * actual connection. For temporary/one-off connections see WifiNetworkSpecifier
 * (not recommended: connection drops when app goes background).
 *
 * Methods:
 *  - requestPermissions()    – optional explicit permission request
 *  - checkWifiEnabled()      – return boolean Wi-Fi state
 *  - scanWifiNetworks()      – scan available SSIDs (handles throttle gracefully)
 *  - connectToWifi()         – connect to a given SSID (with optional password)
 */
class WifiModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  companion object {
    const val NAME = "WifiModule"
    private val WIFI_PERMISSIONS = arrayOf(
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_WIFI_STATE,
      Manifest.permission.CHANGE_WIFI_STATE
    )
    private const val PERMISSION_REQUEST_CODE = 42
  }

  override fun getName(): String = NAME

  private val wifiManager: WifiManager? by lazy {
    reactContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  }

  private val connectivityManager: ConnectivityManager? by lazy {
    reactContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  }

  // ─── Permission helpers ────────────────────────────────────────────────────

  private fun hasPermissions(): Boolean = WIFI_PERMISSIONS.all {
    ActivityCompat.checkSelfPermission(reactContext, it) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Internal helper: if permissions are already granted, calls [onGranted] immediately.
   * Otherwise triggers the system permission dialog and calls [onGranted] upon success,
   * or rejects [promise] if denied.
   */
  private fun ensurePermissionsThen(promise: Promise, onGranted: () -> Unit) {
    if (hasPermissions()) {
      onGranted()
      return
    }

    val activity = getCurrentActivity() ?: reactContext.currentActivity
    if (activity == null) {
      promise.reject(WifiError.PERMISSION_DENIED.code, "No active Activity to request permissions from.")
      return
    }

    val permissionAwareActivity = activity as? PermissionAwareActivity ?: run {
      promise.reject(WifiError.PERMISSION_DENIED.code, "Activity does not support runtime permissions.")
      return
    }

    val missing = WIFI_PERMISSIONS.filter {
      ActivityCompat.checkSelfPermission(reactContext, it) != PackageManager.PERMISSION_GRANTED
    }

    permissionAwareActivity.requestPermissions(
      missing.toTypedArray(),
      PERMISSION_REQUEST_CODE,
      object : PermissionListener {
        override fun onRequestPermissionsResult(
          requestCode: Int,
          permissions: Array<String>,
          grantResults: IntArray
        ): Boolean {
          if (requestCode != PERMISSION_REQUEST_CODE) return false
          val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
          if (allGranted) {
            onGranted()
          } else {
            val neverAsk = permissions.zip(grantResults.toList()).any { (perm, result) ->
              result == PackageManager.PERMISSION_DENIED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
            val msg = if (neverAsk)
              "Permissions permanently denied. Ask user to enable them in Settings."
            else
              WifiError.PERMISSION_DENIED.message
            promise.reject(WifiError.PERMISSION_DENIED.code, msg)
          }
          return true
        }
      }
    )
  }

  // ─── Public: explicit permission request ──────────────────────────────────

  /**
   * Optional explicit permission request.
   * The app can call this at a convenient moment (e.g., onboarding) to pre-grant
   * permissions before the first scan. Each Wi-Fi method will also call this
   * automatically if needed.
   *
   * Resolves with: "granted" | "denied" | "never_ask_again"
   */
  @ReactMethod
  fun requestPermissions(promise: Promise) {
    if (hasPermissions()) {
      promise.resolve("granted")
      return
    }

    val activity = getCurrentActivity() ?: reactContext.currentActivity
    if (activity == null) {
      promise.reject(WifiError.PERMISSION_DENIED.code, "No active Activity to request permissions from.")
      return
    }

    val permissionAwareActivity = activity as? PermissionAwareActivity ?: run {
      promise.reject(WifiError.PERMISSION_DENIED.code, "Activity does not support runtime permissions.")
      return
    }

    val missing = WIFI_PERMISSIONS.filter {
      ActivityCompat.checkSelfPermission(reactContext, it) != PackageManager.PERMISSION_GRANTED
    }

    permissionAwareActivity.requestPermissions(
      missing.toTypedArray(),
      PERMISSION_REQUEST_CODE,
      object : PermissionListener {
        override fun onRequestPermissionsResult(
          requestCode: Int,
          permissions: Array<String>,
          grantResults: IntArray
        ): Boolean {
          if (requestCode != PERMISSION_REQUEST_CODE) return false
          val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
          val neverAsk = grantResults.any { it == PackageManager.PERMISSION_DENIED } &&
            permissions.zip(grantResults.toList()).any { (perm, result) ->
              result == PackageManager.PERMISSION_DENIED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
          promise.resolve(
            when {
              allGranted -> "granted"
              neverAsk   -> "never_ask_again"
              else       -> "denied"
            }
          )
          return true
        }
      }
    )
  }

  // ─── Check Wi-Fi state ────────────────────────────────────────────────────

  @ReactMethod
  fun checkWifiEnabled(promise: Promise) {
    val mgr = wifiManager ?: run {
      promise.reject(WifiError.UNKNOWN.code, WifiError.UNKNOWN.message)
      return
    }
    @Suppress("DEPRECATION")
    promise.resolve(mgr.isWifiEnabled)
  }

  // ─── Scan ─────────────────────────────────────────────────────────────────

  /**
   * Scans for available Wi-Fi networks and resolves with an array of [WifiNetwork] objects.
   *
   * ### Throttle handling (Android 9+ / API 28+)
   * When the scan is throttled (startScan returns false, or EXTRA_RESULTS_UPDATED is false),
   * cached results from [WifiManager.getScanResults] are returned with `isCached = true`.
   * This avoids rejecting the promise unnecessarily — the consumer can inspect `isCached`
   * and `timestamp` to decide whether to retry.
   *
   * ### Permission
   * Automatically requests permissions if not already granted before triggering the scan.
   */
  @ReactMethod
  fun scanWifiNetworks(promise: Promise) {
    ensurePermissionsThen(promise) {
      doScan(promise)
    }
  }

  private fun doScan(promise: Promise) {
    val mgr = wifiManager ?: run {
      promise.reject(WifiError.UNKNOWN.code, WifiError.UNKNOWN.message)
      return
    }

    @Suppress("DEPRECATION")
    if (!mgr.isWifiEnabled) {
      promise.reject(WifiError.WIFI_DISABLED.code, WifiError.WIFI_DISABLED.message)
      return
    }

    val scanTimestamp = System.currentTimeMillis()

    // Register a one-shot BroadcastReceiver for scan completion.
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        reactContext.unregisterReceiver(this)

        val freshScan = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
        @Suppress("DEPRECATION")
        val results = mgr.scanResults

        if (results.isEmpty()) {
          // Throttled AND no cached results available at all.
          promise.reject(WifiError.SCAN_FAILED.code, WifiError.SCAN_FAILED.message)
          return
        }

        // isCached = true when the scan was throttled (freshScan = false).
        val isCached = !freshScan
        val array = Arguments.createArray()
        results.forEach {
          array.pushMap(NetworkUtils.scanResultToMap(it, isCached, scanTimestamp))
        }
        promise.resolve(array)
      }
    }

    reactContext.registerReceiver(
      receiver,
      IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
    )

    @Suppress("DEPRECATION")
    val started = mgr.startScan()

    if (!started) {
      // startScan() rejected immediately (heavy throttle). Unregister receiver
      // and return cached results right away rather than waiting for broadcast.
      reactContext.unregisterReceiver(receiver)

      @Suppress("DEPRECATION")
      val cached = mgr.scanResults
      if (cached.isNotEmpty()) {
        val array = Arguments.createArray()
        cached.forEach { array.pushMap(NetworkUtils.scanResultToMap(it, isCached = true, scanTimestamp)) }
        promise.resolve(array)
      } else {
        promise.reject(WifiError.SCAN_FAILED.code, WifiError.SCAN_FAILED.message)
      }
    }
    // If started = true, the BroadcastReceiver will handle the result.
  }

  // ─── Connect ──────────────────────────────────────────────────────────────

  /**
   * Connects to a Wi-Fi network.
   *
   * On Android 10+ (API 29+): uses [WifiNetworkSuggestion] for a **persistent** connection.
   *   - The OS registers the suggestion and connects automatically when the SSID is in range.
   *   - Success callback fires when the suggestion is accepted, NOT when the device connects.
   *   - To remove the suggestion, call [disconnectFromWifi].
   *
   * On Android < 10: uses the legacy [WifiConfiguration] API for an immediate connection.
   *
   * ### Permission
   * Automatically requests permissions if not already granted before attempting connection.
   *
   * @param ssid     The target network SSID.
   * @param password The WPA2 passphrase, or null for open networks.
   * @param options  Optional map: `hidden` (Boolean).
   */
  @ReactMethod
  fun connectToWifi(ssid: String, password: String?, options: ReadableMap?, promise: Promise) {
    ensurePermissionsThen(promise) {
      doConnect(ssid, password, options, promise)
    }
  }

  private fun doConnect(ssid: String, password: String?, options: ReadableMap?, promise: Promise) {
    val mgr = wifiManager ?: run {
      promise.reject(WifiError.UNKNOWN.code, WifiError.UNKNOWN.message)
      return
    }

    @Suppress("DEPRECATION")
    if (!mgr.isWifiEnabled) {
      promise.reject(WifiError.WIFI_DISABLED.code, WifiError.WIFI_DISABLED.message)
      return
    }

    val hidden = options?.takeIf { it.hasKey("hidden") }?.getBoolean("hidden") ?: false

    NetworkUtils.connectToNetwork(
      wifiManager = mgr,
      ssid = ssid,
      password = password,
      hidden = hidden,
      onSuccess = { promise.resolve(null) },
      onError = { error -> promise.reject(error.code, error.message) }
    )
  }
}
