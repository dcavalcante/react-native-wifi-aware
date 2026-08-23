package com.wifiaware

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.WifiAwareManager
import androidx.annotation.RequiresApi

class WifiAwareModule(reactContext: ReactApplicationContext) :
  NativeWifiAwareSpec(reactContext) {

  override fun getCapabilities(): WritableMap {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return capabilityMap(isSupported = false, isAvailable = false)
    }

    return getCapabilitiesApi26()
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun getCapabilitiesApi26(): WritableMap {
    val supported = reactApplicationContext.packageManager.hasSystemFeature(
      PackageManager.FEATURE_WIFI_AWARE
    )
    val manager = reactApplicationContext.getSystemService(Context.WIFI_AWARE_SERVICE)
      as? WifiAwareManager
    return capabilityMap(supported, supported && manager?.isAvailable == true)
  }

  private fun capabilityMap(isSupported: Boolean, isAvailable: Boolean): WritableMap =
    Arguments.createMap().apply {
      putBoolean("isSupported", isSupported)
      putBoolean("isAvailable", isAvailable)
    }

  companion object {
    const val NAME = NativeWifiAwareSpec.NAME
  }
}
