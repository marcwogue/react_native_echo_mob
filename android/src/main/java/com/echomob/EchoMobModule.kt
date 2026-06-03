package com.echomob

import com.facebook.react.bridge.ReactApplicationContext

class EchoMobModule(reactContext: ReactApplicationContext) :
  NativeEchoMobSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  override fun getDayGreeting(n: Double): String {
    val days = arrayOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi")
    val index = ((n.toInt() % 7) + 7) % 7
    return "bonjour ${days[index]}"
  }

  companion object {
    const val NAME = NativeEchoMobSpec.NAME
  }
}
