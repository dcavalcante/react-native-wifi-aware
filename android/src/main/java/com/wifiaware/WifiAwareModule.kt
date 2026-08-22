package com.wifiaware

import com.facebook.react.bridge.ReactApplicationContext

class WifiAwareModule(reactContext: ReactApplicationContext) :
  NativeWifiAwareSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeWifiAwareSpec.NAME
  }
}
