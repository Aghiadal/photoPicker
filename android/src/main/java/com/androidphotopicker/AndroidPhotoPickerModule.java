package com.androidphotopicker;


import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;


@ReactModule(name = AndroidPhotoPickerModule.NAME)
public class AndroidPhotoPickerModule extends ReactContextBaseJavaModule {
  public static final String NAME = "AndroidPhotoPicker";
  final PhotoPickerImp photoPickerImp;

  public AndroidPhotoPickerModule(ReactApplicationContext context) {
    super(context);
    photoPickerImp = new PhotoPickerImp(context);
  }


  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void launchImageLibrary(final ReadableMap options, final Callback callback) {
    photoPickerImp.launchImageLibrary(options, callback);
  }
}
