package com.androidphotopicker

import com.facebook.react.bridge.ReadableMap

class Options(options: ReadableMap) {
    var selectionLimit: Int = 1
    var videoQuality: Int = 1
    var quality: Int
    var conversionQuality: Int = 92
    var convertToJpeg: Boolean = true
    var maxWidth: Int = 0
    var maxHeight: Int = 0
    var useFrontCamera: Boolean = false
    var mediaType: String? = options.getString("mediaType")

    init {
        if (options.hasKey("selectionLimit")) {
            selectionLimit = options.getInt("selectionLimit")
        }

        val videoQualityString = options.getString("videoQuality")
        if (!videoQualityString.isNullOrEmpty() && videoQualityString.lowercase() != "high") {
            videoQuality = 0
        }

        if (options.hasKey("conversionQuality")) {
            conversionQuality = (options.getDouble("conversionQuality") * 100).toInt()
        }

        val assetRepresentationMode = options.getString("assetRepresentationMode")
        if (!assetRepresentationMode.isNullOrEmpty() && assetRepresentationMode.lowercase() == "current") {
            convertToJpeg = false
        }

        if (options.getString("cameraType") == "front") {
            useFrontCamera = true
        }

        quality = (options.getDouble("quality") * 100).toInt()

        if (options.hasKey("maxHeight")) {
            maxHeight = options.getInt("maxHeight")
        }

        if (options.hasKey("maxWidth")) {
            maxWidth = options.getInt("maxWidth")
        }
    }
}

