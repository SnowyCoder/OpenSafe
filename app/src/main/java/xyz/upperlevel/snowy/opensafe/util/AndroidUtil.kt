package xyz.upperlevel.snowy.opensafe.util

import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import java.io.ByteArrayInputStream
import java.io.File
import java.security.SecureRandom
import java.util.*
import kotlin.math.ceil
import kotlin.math.min
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth




object AndroidUtil {
    val density = Resources.getSystem().displayMetrics.density
    private var _secureRandomInstance: SecureRandom? = null

    fun dp(value: Int): Int {
        return ceil(density * value).toInt()
    }

    fun dpf(value: Float): Float {
        return density * value
    }

    fun loadThumbnail(data: ByteArray, width: Int, height: Int): Bitmap?  {
        // https://stackoverflow.com/a/27013588/4279355
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true

        BitmapFactory.decodeByteArray(data, 0, data.size, opts)

        val widthScale = opts.outWidth / width
        val heightScale = opts.outHeight / height
        val scale = min(widthScale, heightScale)

        var sampleSize = 1
        while (sampleSize < scale) {
            sampleSize *= 2
        }
        opts.inSampleSize = sampleSize // this value must be a power of 2,
        // this is why you can not have an image scaled as you would like
        opts.inJustDecodeBounds = false // now we want to load the image

        // Let's load just the part of the image necessary for creating the thumbnail, not the whole image
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        return correctBitmapOrientation(bitmap, data)
    }

    val secureRandomInstance: SecureRandom
        get() {
            if (_secureRandomInstance == null) {
                _secureRandomInstance = SecureRandom()
            }
            return _secureRandomInstance!!
        }

    fun File.guessMimeType(): String? {
        return guessMimeType(toString())
    }

    fun guessMimeType(str: String): String? {
        return MimeTypeMap.getFileExtensionFromUrl(str)?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.toLowerCase(Locale.US))
        }
    }

    fun Fragment.ensurePermissions(permission: Array<String>, explaination: String, permissionCode: Int): Boolean {
        val rejectedPermissions = permission
            .filter { checkSelfPermission(context!!, it) != PERMISSION_GRANTED }
            .toTypedArray()

        if (rejectedPermissions.isEmpty()) return true

        // Should we show an explanation?
        if (rejectedPermissions.any { shouldShowRequestPermissionRationale(it) }) {
            AlertDialog.Builder(context!!)
                .setTitle("Permission Request")
                .setMessage(explaination)
                .setNeutralButton(android.R.string.ok) { dialogInterface, i ->
                    requestPermissions(rejectedPermissions, permissionCode)
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        } else {
            requestPermissions(rejectedPermissions, permissionCode)
        }

        return false
    }

    fun loadBitmap(data: ByteArray): Bitmap {
        val img = BitmapFactory.decodeByteArray(data, 0, data.size)
        return correctBitmapOrientation(img, data)
    }

    fun correctBitmapOrientation(img: Bitmap, data: ByteArray): Bitmap {
        val exif = ExifInterface(ByteArrayInputStream(data))
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate( 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.setScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setScale(1f, -1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate (270f)
                matrix.postScale (-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate (90f)
                matrix.postScale (-1f, 1f)
            }
            else -> return img
        }

        val rotated = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotated
    }

    //fun loadRotationFix()
}