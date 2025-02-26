package com.androidphotopicker

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.annotation.Nullable
import androidx.exifinterface.media.ExifInterface
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Collections
import java.util.List
import java.util.UUID

class Utils {
    companion object {
        @JvmField
        var fileNamePrefix: String = "rn_image_picker_lib_temp_"
        @JvmField
        var errOthers: String = "others"
        @JvmField
        var mediaTypePhoto: String = "photo"
        @JvmField
        var mediaTypeVideo: String = "video"

        fun createFile(reactContext: Context, fileType: String): File? {
            try {
                val filename = fileNamePrefix + UUID.randomUUID().toString() + "." + fileType

                // getCacheDir will auto-clean according to android docs
                val fileDir = reactContext.cacheDir

                val file = File(fileDir, filename)
                file.createNewFile()
                return file
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun copyUri(fromUri: Uri, toUri: Uri, resolver: ContentResolver) {
            try {
                resolver.openOutputStream(toUri).use { os ->
                    resolver.openInputStream(fromUri).use { ins ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (ins!!.read(buffer).also { bytesRead = it } != -1) {
                            os!!.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // Make a copy of shared storage files inside app specific storage so that users can access it later.
        fun getAppSpecificStorageUri(sharedStorageUri: Uri?, context: Context): Uri? {
            if (sharedStorageUri == null) {
                return null
            }
            val contentResolver = context.contentResolver
            var fileType = getFileTypeFromMime(contentResolver.getType(sharedStorageUri))

            if (fileType == null) {
                val cursor: Cursor? = contentResolver.query(sharedStorageUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val fileName = it.getString(nameIndex)
                        val lastDotIndex = fileName.lastIndexOf('.')

                        if (lastDotIndex != -1) {
                            fileType = fileName.substring(lastDotIndex + 1)
                        }
                    }
                }
            }

            val file = createFile(context, fileType)
            val toUri = Uri.fromFile(file)
            copyUri(sharedStorageUri, toUri, contentResolver)
            return toUri
        }

        fun getImageDimensions(uri: Uri, reactContext: Context): IntArray {
            try {
                reactContext.contentResolver.openInputStream(uri).use { inputStream ->
                    val orientation = getOrientation(uri, reactContext)
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(inputStream, null, options)
                    return if (needToSwapDimension(orientation)) {
                        intArrayOf(options.outHeight, options.outWidth)
                    } else {
                        intArrayOf(options.outWidth, options.outHeight)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return intArrayOf(0, 0)
            }
        }

        fun getBase64String(uri: Uri, reactContext: Context): String? {
            try {
                reactContext.contentResolver.openInputStream(uri).use { inputStream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    val bytes = output.toByteArray()
                    return Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }

        private fun needToSwapDimension(orientation: String): Boolean {
            return orientation == ExifInterface.ORIENTATION_ROTATE_90.toString() ||
                    orientation == ExifInterface.ORIENTATION_ROTATE_270.toString()
        }

        private fun shouldConvertToJpeg(mimeType: String?, options: Options): Boolean {
            return options.convertToJpeg && mimeType != null && (mimeType == "image/heic" || mimeType == "image/heif")
        }

        // Resize image and/or convert it from HEIC/HEIF to JPEG
        // When decoding a jpg to bitmap all exif meta data will be lost, so make sure to copy orientation exif to new file else image might have wrong orientations
        fun resizeOrConvertImage(uri: Uri, context: Context, options: Options): Uri {
            try {
                val origDimens = getImageDimensions(uri, context)
                var mimeType = getMimeType(uri, context)

                val targetQuality: Int

                if (!shouldResizeImage(origDimens[0], origDimens[1], options)) {
                    if (shouldConvertToJpeg(mimeType, options)) {
                        mimeType = "image/jpeg"
                        targetQuality = options.conversionQuality
                    } else {
                        return uri
                    }
                } else {
                    targetQuality = options.quality
                }

                val newDimens = getImageDimensBasedOnConstraints(origDimens[0], origDimens[1], options)

                context.contentResolver.openInputStream(uri).use { imageStream ->
                    var b: Bitmap = BitmapFactory.decodeStream(imageStream)
                    val originalOrientation = getOrientation(uri, context)

                    b = if (needToSwapDimension(originalOrientation)) {
                        Bitmap.createScaledBitmap(b, newDimens[1], newDimens[0], true)
                    } else {
                        Bitmap.createScaledBitmap(b, newDimens[0], newDimens[1], true)
                    }

                    val file = createFile(context, getFileTypeFromMime(mimeType))
                    context.contentResolver.openOutputStream(Uri.fromFile(file)).use { os ->
                        b.compress(getBitmapCompressFormat(mimeType), targetQuality, os)
                    }

                    setOrientation(file, originalOrientation, context)

                    deleteFile(uri)

                    return Uri.fromFile(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return uri // cannot resize the image, return the original uri
            }
        }

        @Throws(IOException::class)
        fun getOrientation(uri: Uri, context: Context): String {
            val exifInterface = ExifInterface(context.contentResolver.openInputStream(uri)!!)
            return exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION)
        }

        // ExifInterface.saveAttributes is costly operation so don't set exif for unnecessary orientations
        @Throws(IOException::class)
        fun setOrientation(file: File?, orientation: String, context: Context) {
            if (orientation == ExifInterface.ORIENTATION_NORMAL.toString() ||
                    orientation == ExifInterface.ORIENTATION_UNDEFINED.toString()
            ) {
                return
            }
            val exifInterface = ExifInterface(file!!)
            exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, orientation)
            exifInterface.saveAttributes()
        }

        fun getImageDimensBasedOnConstraints(origWidth: Int, origHeight: Int, options: Options): IntArray {
            var width = origWidth
            var height = origHeight

            if (options.maxWidth == 0 || options.maxHeight == 0) {
                return intArrayOf(width, height)
            }

            if (options.maxWidth < width) {
                height = ((options.maxWidth.toFloat() / width) * height).toInt()
                width = options.maxWidth
            }

            if (options.maxHeight < height) {
                width = ((options.maxHeight.toFloat() / height) * width).toInt()
                height = options.maxHeight
            }

            return intArrayOf(width, height)
        }

        fun getFileSize(uri: Uri, context: Context): Double {
            try {
                context.contentResolver.openFileDescriptor(uri, "r").use { f ->
                    return f!!.statSize.toDouble()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return 0.0
            }
        }

        fun shouldResizeImage(origWidth: Int, origHeight: Int, options: Options): Boolean {
            if ((options.maxWidth == 0 || options.maxHeight == 0) && options.quality == 100) {
                return false
            }

            if (options.maxWidth >= origWidth && options.maxHeight >= origHeight && options.quality == 100) {
                return false
            }

            return true
        }

        fun getBitmapCompressFormat(mimeType: String): Bitmap.CompressFormat {
            return when (mimeType) {
                "image/jpeg" -> Bitmap.CompressFormat.JPEG
                "image/png" -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
        }

        fun getFileTypeFromMime(mimeType: String?): String {
            if (mimeType == null) {
                return "jpg"
            }
            return when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/gif" -> "gif"
                else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
        }

        fun deleteFile(uri: Uri) {
            File(uri.path).delete()
        }

        // Since library users can have many modules in their project, we should respond to onActivityResult only for our request.
        fun isValidRequestCode(requestCode: Int): Boolean {
            return requestCode == PhotoPickerImp.REQUEST_LAUNCH_LIBRARY
        }

        fun isImageType(uri: Uri, context: Context): Boolean {
            return isContentType("image/", uri, context)
        }

        fun isContentType(contentMimeType: String, uri: Uri, context: Context): Boolean {
            val mimeType = getMimeType(uri, context)
            if (mimeType != null) {
                return mimeType.contains(contentMimeType)
            }
            return false
        }

        fun getMimeType(uri: Uri, context: Context): String {
            if (uri.scheme.equals("file")) {
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                )
            } else if (uri.scheme.equals("content")) {
                val contentResolver = context.contentResolver
                val contentResolverMimeType = contentResolver.getType(uri)
                if (contentResolverMimeType.isNullOrBlank()) {
                    return getMimeTypeForContent(uri, context) ?: "Unknown"
                } else {
                    return contentResolverMimeType
                }
            }
            return "Unknown"
        }

        fun getMimeTypeForContent(uri: Uri, context: Context): String? {
            val fileName = getFileNameForContent(uri, context)
            var fileType: String = "Unknown"

            val lastDotIndex = fileName.lastIndexOf('.')
            if (lastDotIndex != -1) {
                fileType = fileName.substring(lastDotIndex + 1)
            }
            return fileType
        }

        fun getFileName(uri: Uri, context: Context): String {
            if (uri.scheme.equals("file")) {
                return uri.lastPathSegment ?: "Unknown"
            } else if (uri.scheme.equals("content")) {
                return getFileNameForContent(uri, context)
            }
            return "Unknown"
        }

        private fun getFileNameForContent(uri: Uri, context: Context): String {
            val contentResolver = context.contentResolver
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            var fileName = uri.lastPathSegment ?: "Unknown"
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
