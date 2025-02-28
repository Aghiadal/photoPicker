package com.androidphotopicker;

import com.facebook.react.bridge.ReadableMap;

import android.text.TextUtils;

public class Options {
  int selectionLimit = 1;
  int videoQuality = 1;
  int quality;
  int conversionQuality = 92;
  Boolean convertToJpeg = true;
  int maxWidth;
  int maxHeight;
  Boolean useFrontCamera = false;
  String mediaType;


  Options(ReadableMap options) {
    mediaType = options.getString("mediaType");

    if(options.hasKey("selectionLimit")) {
      selectionLimit = options.getInt("selectionLimit");
    }

    String videoQualityString = options.getString("videoQuality");
    if (!TextUtils.isEmpty(videoQualityString) && !videoQualityString.toLowerCase().equals("high")) {
      videoQuality = 0;
    }

    if (options.hasKey("conversionQuality")) {
      conversionQuality = (int) (options.getDouble("conversionQuality") * 100);
    }

    String assetRepresentationMode = options.getString("assetRepresentationMode");
    if (!TextUtils.isEmpty(assetRepresentationMode) && assetRepresentationMode.toLowerCase().equals("current")) {
      convertToJpeg = false;
    }

    if (options.getString("cameraType").equals("front")) {
      useFrontCamera = true;
    }

    quality = (int) (options.getDouble("quality") * 100);

    if (options.hasKey("maxHeight")) {
      maxHeight = options.getInt("maxHeight");
    }

    if (options.hasKey("maxWidth")) {
      maxWidth = options.getInt("maxWidth");
    }
  }
}
