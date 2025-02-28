package com.androidphotopicker;

import static com.androidphotopicker.Utils.collectUrisFromData;
import static com.androidphotopicker.Utils.errOthers;
import static com.androidphotopicker.Utils.getCancelMap;
import static com.androidphotopicker.Utils.getErrorMap;
import static com.androidphotopicker.Utils.getResponseMap;
import static com.androidphotopicker.Utils.isValidRequestCode;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoPickerImp implements ActivityEventListener {
  public static final int REQUEST_LAUNCH_LIBRARY = 13003;
  private ReactApplicationContext reactContext;
  Callback callback;
  Options options;
  public PhotoPickerImp(ReactApplicationContext reactContext) {
    this.reactContext = reactContext;
    this.reactContext.addActivityEventListener(this);
  }


  public void launchImageLibrary(final ReadableMap options , final Callback callback) {
    final Activity currentActivity = this.reactContext.getCurrentActivity();
    if (currentActivity == null) {
      callback.invoke(getErrorMap(errOthers, "Activity error"));
      return;
    }

    this.callback = callback;
    this.options = new Options(options);

    PickVisualMedia.VisualMediaType mediaType;
    PickVisualMediaRequest mediaRequest;

    int requestCode;
    Intent libraryIntent;
    requestCode = REQUEST_LAUNCH_LIBRARY;

    int selectionLimit = this.options.selectionLimit;
    boolean isSingleSelect = selectionLimit == 1;


    mediaType = (PickVisualMedia.VisualMediaType) PickVisualMedia.ImageOnly.INSTANCE;

    mediaRequest = new PickVisualMediaRequest.Builder().setMediaType(mediaType).build();

    if (isSingleSelect) {
      libraryIntent = new PickVisualMedia().createIntent(this.reactContext.getApplicationContext(), mediaRequest);
    } else {
      ActivityResultContracts.PickMultipleVisualMedia pickMultipleVisualMedia = selectionLimit > 1
        ? new ActivityResultContracts.PickMultipleVisualMedia(selectionLimit)
        : new ActivityResultContracts.PickMultipleVisualMedia();
      libraryIntent = pickMultipleVisualMedia.createIntent(this.reactContext.getApplicationContext(), mediaRequest);
    }

    try {
      currentActivity.startActivityForResult(libraryIntent, requestCode);
    } catch (ActivityNotFoundException e) {
      callback.invoke(getErrorMap(errOthers, e.getMessage()));
      this.callback = null;
    }
  }

  void onAssetsObtained(List<Uri> fileUris) {
    Log.e("fileUris", String.valueOf(fileUris));
    ExecutorService executor = Executors.newSingleThreadExecutor();

    executor.submit(() -> {
      try {
        callback.invoke(getResponseMap(fileUris, options, reactContext));
      } catch (RuntimeException exception) {
        callback.invoke(getErrorMap(errOthers, exception.getMessage()));
      } finally {
        callback = null;
      }
    });
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

    Log.e("requestCode", String.valueOf(requestCode));
    if (!isValidRequestCode(requestCode) || (this.callback == null)) {
      return;
    }

    if (resultCode != Activity.RESULT_OK) {
      try {
        callback.invoke(getCancelMap());
        return;
      } catch (RuntimeException exception) {
        callback.invoke(getErrorMap(errOthers, exception.getMessage()));
      } finally {
        callback = null;
      }
    }

    if (requestCode == REQUEST_LAUNCH_LIBRARY) {
      onAssetsObtained(collectUrisFromData(data));
    }
  }

  @Override
  public void onNewIntent(Intent intent) {

  }
}
