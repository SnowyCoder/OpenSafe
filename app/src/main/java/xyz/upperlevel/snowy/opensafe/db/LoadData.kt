package xyz.upperlevel.snowy.opensafe.db

import android.graphics.Bitmap
import java.io.InputStream

class LoadData {
    var info: FileInfo? = null
    var thumbnail: Bitmap? = null
    var data: InputStream? = null

    enum class LoadRequest {
        FILE_INFO, THUMBNAIL, DATA
    }
}