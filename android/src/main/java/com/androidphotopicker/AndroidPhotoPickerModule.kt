package com.androidphotopicker

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.Callback

@ReactModule(name = AndroidPhotoPickerModule.NAME)
class AndroidPhotoPickerModule(reactContext: ReactApplicationContext) :
  NativeAndroidPhotoPickerSpec(reactContext) {

  override fun getName(): String {
    return NAME
  }


  @ReactMethod
  fun launchImageLibrary(options: ReadableMap, callback: Callback) {
    photoPickerImp.launchImageLibrary(options, callback)
  }

  companion object {
    const val NAME = "AndroidPhotoPicker"
  }
}
