package xyz.upperlevel.snowy.opensafe.db

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.guessMimeType
import java.io.File


@Serializable
@Parcelize
@SuppressLint("ParcelCreator")
data class FileInfo(
    val name: String,
    val length: Long,
    var originalPath: String?,
    var mimeType: String?,
    var lastModified: Long?
    ) : Parcelable {

    constructor(file: File) : this(
        file.name,
        file.length(),
        file.absolutePath,
        file.guessMimeType(),
        file.lastModified()
    )
}