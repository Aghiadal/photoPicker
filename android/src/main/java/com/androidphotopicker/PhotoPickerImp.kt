package com.androidphotopicker

import static com.mashreqmobileapp.photoPicker.Utils.collectUrisFromData
import static com.mashreqmobileapp.photoPicker.Utils.errOthers
import static com.mashreqmobileapp.photoPicker.Utils.getCancelMap
import static com.mashreqmobileapp.photoPicker.Utils.getErrorMap
import static com.mashreqmobileapp.photoPicker.Utils.getResponseMap
import static com.mashreqmobileapp.photoPicker.Utils.isValidRequestCode
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import java.util.List
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoPickerImp(private val reactContext: ReactApplicationContext) : ActivityEventListener {

    companion object {
        const val REQUEST_LAUNCH_LIBRARY = 13003
    }

    var callback: Callback? = null
    lateinit var options: Options

    init {
        this.reactContext.addActivityEventListener(this)
    }

    fun launchImageLibrary(options: ReadableMap, callback: Callback) {
        val currentActivity: Activity? = this.reactContext.currentActivity
        if (currentActivity == null) {
            callback.invoke(getErrorMap(errOthers, "Activity error"))
            return
        }

        this.callback = callback
        this.options = Options(options)

        val mediaType: PickVisualMedia.VisualMediaType
        val mediaRequest: PickVisualMediaRequest
        val requestCode: Int
        var libraryIntent: Intent

        requestCode = REQUEST_LAUNCH_LIBRARY

        val selectionLimit = this.options.selectionLimit
        val isSingleSelect = selectionLimit == 1

        mediaType = PickVisualMedia.ImageOnly.INSTANCE

        mediaRequest = PickVisualMediaRequest.Builder().setMediaType(mediaType).build()

        if (isSingleSelect) {
            libraryIntent = PickVisualMedia().createIntent(this.reactContext.applicationContext, mediaRequest)
        } else {
            val pickMultipleVisualMedia: ActivityResultContracts.PickMultipleVisualMedia = if (selectionLimit > 1)
                ActivityResultContracts.PickMultipleVisualMedia(selectionLimit)
            else
                ActivityResultContracts.PickMultipleVisualMedia()
            libraryIntent = pickMultipleVisualMedia.createIntent(this.reactContext.applicationContext, mediaRequest)
        }

        try {
            currentActivity.startActivityForResult(libraryIntent, requestCode)
        } catch (e: ActivityNotFoundException) {
            callback.invoke(getErrorMap(errOthers, e.message))
            this.callback = null
        }
    }

    fun onAssetsObtained(fileUris: List<Uri>) {
        Log.e("fileUris", String.valueOf(fileUris))
        val executor: ExecutorService = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                callback?.invoke(getResponseMap(fileUris, options, reactContext))
            } catch (exception: RuntimeException) {
                callback?.invoke(getErrorMap(errOthers, exception.message))
            } finally {
                callback = null
            }
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        Log.e("requestCode", String.valueOf(requestCode))
        if (!isValidRequestCode(requestCode) || (callback == null)) {
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            try {
                callback?.invoke(getCancelMap())
                return
            } catch (exception: RuntimeException) {
                callback?.invoke(getErrorMap(errOthers, exception.message))
            } finally {
                callback = null
            }
        }

        if (requestCode == REQUEST_LAUNCH_LIBRARY) {
            onAssetsObtained(collectUrisFromData(data))
        }
    }

    override fun onNewIntent(intent: Intent) {
    }
}
