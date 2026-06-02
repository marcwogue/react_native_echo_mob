package com.echomob

import com.facebook.react.bridge.ReactApplicationContext

class EchoMobModule(reactContext: ReactApplicationContext) :
  NativeEchoMobSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeEchoMobSpec.NAME
  }
}
