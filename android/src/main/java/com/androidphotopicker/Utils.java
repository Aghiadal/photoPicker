package com.androidphotopicker;


import static com.androidphotopicker.PhotoPickerImp.REQUEST_LAUNCH_LIBRARY;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Utils {
  public static String fileNamePrefix = "rn_image_picker_lib_temp_";
  public static String errOthers = "others";
  public static String mediaTypePhoto = "photo";
  public static String mediaTypeVideo = "video";

  public static File createFile(Context reactContext, String fileType) {
    try {
      String filename = fileNamePrefix + UUID.randomUUID() + "." + fileType;

      // getCacheDir will auto-clean according to android docs
      File fileDir = reactContext.getCacheDir();

      File file = new File(fileDir, filename);
      file.createNewFile();
      return file;

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static void copyUri(Uri fromUri, Uri toUri, ContentResolver resolver) {
    try (OutputStream os = resolver.openOutputStream(toUri);
         InputStream is = resolver.openInputStream(fromUri)) {

      byte[] buffer = new byte[8192];
      int bytesRead;

      while ((bytesRead = is.read(buffer)) != -1) {
        os.write(buffer, 0, bytesRead);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Make a copy of shared storage files inside app specific storage so that users can access it later.
  public static Uri getAppSpecificStorageUri(Uri sharedStorageUri, Context context) {
    if (sharedStorageUri == null) {
      return null;
    }
    ContentResolver contentResolver = context.getContentResolver();
    String fileType = getFileTypeFromMime(contentResolver.getType(sharedStorageUri));

    if (fileType == null) {
      Cursor cursor =
        contentResolver.query(sharedStorageUri, null, null, null, null);
      if (cursor.moveToFirst()) {
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        String fileName = cursor.getString(nameIndex);
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex != -1) {
          fileType = fileName.substring(lastDotIndex + 1);
        }
      }
    }

    Uri toUri = Uri.fromFile(createFile(context, fileType));
    copyUri(sharedStorageUri, toUri, contentResolver);
    return toUri;
  }

  public static int[] getImageDimensions(Uri uri, Context reactContext) {
    try (InputStream inputStream = reactContext.getContentResolver().openInputStream(uri)) {

      String orientation = getOrientation(uri,reactContext);

      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(inputStream, null, options);
      if (needToSwapDimension(orientation)) {
        return new int[]{options.outHeight, options.outWidth};
      }else {
        return new int[]{options.outWidth, options.outHeight};
      }

    } catch (IOException e) {
      e.printStackTrace();
      return new int[]{0, 0};
    }
  }


  static String getBase64String(Uri uri, Context reactContext) {
    try (InputStream inputStream = reactContext.getContentResolver().openInputStream(uri);
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] bytes;
      byte[] buffer = new byte[8192];
      int bytesRead;

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }
      bytes = output.toByteArray();
      return Base64.encodeToString(bytes, Base64.NO_WRAP);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private static boolean needToSwapDimension(String orientation){
    return orientation.equals(String.valueOf(ExifInterface.ORIENTATION_ROTATE_90))
      || orientation.equals(String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
  }

  private static boolean shouldConvertToJpeg(String mimeType, Options options) {
    return options.convertToJpeg && mimeType != null && (mimeType.equals("image/heic") || mimeType.equals("image/heif"));
  }

  // Resize image and/or convert it from HEIC/HEIF to JPEG
  // When decoding a jpg to bitmap all exif meta data will be lost, so make sure to copy orientation exif to new file else image might have wrong orientations
  public static Uri resizeOrConvertImage(Uri uri, Context context, Options options) {
    try {
      int[] origDimens = getImageDimensions(uri, context);
      String mimeType = getMimeType(uri, context);

      int targetQuality;

      if (!shouldResizeImage(origDimens[0], origDimens[1], options)) {
        if (shouldConvertToJpeg(mimeType, options)) {
          mimeType = "image/jpeg";
          targetQuality = options.conversionQuality;
        } else {
          return uri;
        }
      } else {
        targetQuality = options.quality;
      }

      int[] newDimens = getImageDimensBasedOnConstraints(origDimens[0], origDimens[1], options);

      try (InputStream imageStream = context.getContentResolver().openInputStream(uri)) {
        Bitmap b = BitmapFactory.decodeStream(imageStream);
        String originalOrientation = getOrientation(uri, context);

        if (needToSwapDimension(originalOrientation)) {
          b = Bitmap.createScaledBitmap(b, newDimens[1], newDimens[0], true);
        } else {
          b = Bitmap.createScaledBitmap(b, newDimens[0], newDimens[1], true);
        }

        File file = createFile(context, getFileTypeFromMime(mimeType));

        try (OutputStream os = context.getContentResolver().openOutputStream(Uri.fromFile(file))) {
          b.compress(getBitmapCompressFormat(mimeType), targetQuality, os);
        }

        setOrientation(file, originalOrientation, context);

        deleteFile(uri);

        return Uri.fromFile(file);
      }

    } catch (Exception e) {
      e.printStackTrace();
      return uri; // cannot resize the image, return the original uri
    }
  }

  static String getOrientation(Uri uri, Context context) throws IOException {
    ExifInterface exifInterface = new ExifInterface(context.getContentResolver().openInputStream(uri));
    return exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION);
  }

  // ExifInterface.saveAttributes is costly operation so don't set exif for unnecessary orientations
  static void setOrientation(File file, String orientation, Context context) throws IOException {
    if (orientation.equals(String.valueOf(ExifInterface.ORIENTATION_NORMAL)) || orientation.equals(String.valueOf(ExifInterface.ORIENTATION_UNDEFINED))) {
      return;
    }
    ExifInterface exifInterface = new ExifInterface(file);
    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, orientation);
    exifInterface.saveAttributes();
  }

  static int[] getImageDimensBasedOnConstraints(int origWidth, int origHeight, Options options) {
    int width = origWidth;
    int height = origHeight;

    if (options.maxWidth == 0 || options.maxHeight == 0) {
      return new int[]{width, height};
    }

    if (options.maxWidth < width) {
      height = (int) (((float) options.maxWidth / width) * height);
      width = options.maxWidth;
    }

    if (options.maxHeight < height) {
      width = (int) (((float) options.maxHeight / height) * width);
      height = options.maxHeight;
    }

    return new int[]{width, height};
  }

  static double getFileSize(Uri uri, Context context) {
    try (ParcelFileDescriptor f = context.getContentResolver().openFileDescriptor(uri, "r")) {
      return f.getStatSize();
    } catch (Exception e) {
      e.printStackTrace();
      return 0;
    }
  }

  static boolean shouldResizeImage(int origWidth, int origHeight, Options options) {
    if ((options.maxWidth == 0 || options.maxHeight == 0) && options.quality == 100) {
      return false;
    }

    if (options.maxWidth >= origWidth && options.maxHeight >= origHeight && options.quality == 100) {
      return false;
    }

    return true;
  }

  static Bitmap.CompressFormat getBitmapCompressFormat(String mimeType) {
    switch (mimeType) {
      case "image/jpeg":
        return Bitmap.CompressFormat.JPEG;
      case "image/png":
        return Bitmap.CompressFormat.PNG;
    }
    return Bitmap.CompressFormat.JPEG;
  }

  static String getFileTypeFromMime(String mimeType) {
    if (mimeType == null) {
      return "jpg";
    }
    switch (mimeType) {
      case "image/jpeg":
        return "jpg";
      case "image/png":
        return "png";
      case "image/gif":
        return "gif";
    }
    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
  }

  static void deleteFile(Uri uri) {
    new File(uri.getPath()).delete();
  }


  // Since library users can have many modules in their project, we should respond to onActivityResult only for our request.
  static boolean isValidRequestCode(int requestCode) {
    return requestCode == REQUEST_LAUNCH_LIBRARY;
  }

  static boolean isImageType(Uri uri, Context context) {
    return Utils.isContentType("image/", uri, context);
  }

  static boolean isContentType(String contentMimeType, Uri uri, Context context) {
    final String mimeType = getMimeType(uri, context);

    if (mimeType != null) {
      return mimeType.contains(contentMimeType);
    }

    return false;
  }

  static String getMimeType(Uri uri, Context context) {
    if (uri.getScheme().equals("file")) {
      return MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()));
    } else if (uri.getScheme().equals("content")) {
      ContentResolver contentResolver = context.getContentResolver();
      String contentResolverMimeType = contentResolver.getType(uri);

      if (contentResolverMimeType.isBlank()) {
        return getMimeTypeForContent(uri, context);
      } else {
        return contentResolverMimeType;
      }
    }

    return "Unknown";
  }

  static @Nullable String getMimeTypeForContent(Uri uri, Context context) {
    String fileName = getFileNameForContent(uri, context);
    String fileType = "Unknown";

    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex != -1) {
      fileType = fileName.substring(lastDotIndex + 1);
    }
    return fileType;
  }

  static String getFileName(Uri uri, Context context) {
    if (uri.getScheme().equals("file")) {
      return uri.getLastPathSegment();
    } else if (uri.getScheme().equals("content")) {
      return getFileNameForContent(uri, context);
    }

    return "Unknown";
  }

  private static String getFileNameForContent(Uri uri, Context context) {
    ContentResolver contentResolver = context.getContentResolver();
    Cursor cursor = contentResolver.query(uri, null, null, null, null);

    String fileName = uri.getLastPathSegment();
    try {
      if (cursor.moveToFirst()) {
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        fileName = cursor.getString(nameIndex);
      }
    } finally {
      cursor.close();
    }
    return fileName;
  }

  static List<Uri> collectUrisFromData(Intent data) {
    // Default Gallery app on older Android versions doesn't support multiple image
    // picking and thus never uses clip data.
    if (data.getClipData() == null) {
      return Collections.singletonList(data.getData());
    }

    ClipData clipData = data.getClipData();
    List<Uri> fileUris = new ArrayList<>(clipData.getItemCount());

    for (int i = 0; i < clipData.getItemCount(); ++i) {
      fileUris.add(clipData.getItemAt(i).getUri());
    }

    return fileUris;
  }

  static ReadableMap getImageResponseMap(Uri uri, Uri appSpecificUri, Context context) {

    String fileName = getFileName(uri, context);
    WritableMap map = Arguments.createMap();
    map.putString("uri", appSpecificUri.toString());
    map.putDouble("fileSize", getFileSize(appSpecificUri, context));
    map.putString("name", fileName);
    map.putString("type", getMimeType(appSpecificUri, context));

    return map;
  }

  static ReadableMap getResponseMap(List<Uri> fileUris, Options options, Context context) throws RuntimeException {
    WritableArray assets = Arguments.createArray();

    for (int i = 0; i < fileUris.size(); ++i) {
      Uri uri = fileUris.get(i);

      Uri appSpecificUrl = uri;
      if (uri.getScheme().contains("content")) {
        appSpecificUrl = getAppSpecificStorageUri(uri, context);
      }

      // Call getAppSpecificStorageUri in the if block to avoid copying unsupported files
      if (isImageType(uri, context)) {
        appSpecificUrl = resizeOrConvertImage(appSpecificUrl, context, options);
        assets.pushMap(getImageResponseMap(uri, appSpecificUrl, context));
      } else {
        throw new RuntimeException("Unsupported file type");
      }
    }

    WritableMap response = Arguments.createMap();
    response.putArray("assets", assets);

    return response;
  }

  static ReadableMap getErrorMap(String errCode, String errMsg) {
    WritableMap map = Arguments.createMap();
    map.putString("errorCode", errCode);
    if (errMsg != null) {
      map.putString("errorMessage", errMsg);
    }
    return map;
  }

  static ReadableMap getCancelMap() {
    WritableMap map = Arguments.createMap();
    map.putBoolean("didCancel", true);
    return map;
  }
}
