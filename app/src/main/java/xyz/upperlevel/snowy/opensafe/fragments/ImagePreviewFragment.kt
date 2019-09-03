package xyz.upperlevel.snowy.opensafe.fragments

import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_image_preview.*
import xyz.upperlevel.snowy.opensafe.R
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.loadBitmap


class ImagePreviewFragment : Fragment() {
    private var thumbnail: Bitmap? = null
    private lateinit var db: Database
    private lateinit var path: String

    private var task: LoadFullImageTask? = null
    private var loadedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments!!.let {
            thumbnail = it.getParcelable(PARAM_THUMBNAIL)
            db = it.getParcelable(PARAM_DB)!!
            path = it.getString(PARAM_PATH)!!
        }
        savedInstanceState?.also {
            loadedBitmap = it.getParcelable("loadedBitmap")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_image_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when {
            loadedBitmap != null -> imagePreview.setImageBitmap(loadedBitmap)
            thumbnail != null -> imagePreview.setImageBitmap(thumbnail!!)
            else -> imagePreview.setImageResource(R.drawable.ic_image_black)
        }

        if (loadedBitmap == null) {
            task = LoadFullImageTask(this, db)
            task!!.execute(path)
        } else {
            popupLogText.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        loadedBitmap?.also { outState.putParcelable("loadedBitmap", it) }
    }

    override fun onDestroy() {
        super.onDestroy()

        task?.also {
            it.fragment = null
            it.cancel(true)
        }
    }

    fun onImageLoaded(result: Bitmap?) {
        if (result != null) {
            popupLogText.visibility = View.GONE
            loadedBitmap = result
            imagePreview.setImageBitmap(result)
        } else {
            popupLogText.text = "Error"
        }
    }

    private class LoadFullImageTask(var fragment: ImagePreviewFragment?, val db: Database) : AsyncTask<String, Void, Bitmap>() {
        override fun doInBackground(vararg paths: String): Bitmap? {
            assert(paths.size == 1)

            val path = paths[0]
            val bitmapData = db.getFs().getFile(path)?.use { it.readBytes() } ?: return null
            val bitmap = loadBitmap(bitmapData)

            if (isCancelled) return null

            return bitmap
        }

        override fun onProgressUpdate(vararg progress: Void) {
        }

        override fun onPostExecute(result: Bitmap?) {
            if (isCancelled) return
            fragment?.onImageLoaded(result)
        }
    }


    companion object {
        const val PARAM_THUMBNAIL = "thumb"
        const val PARAM_DB = "db"
        const val PARAM_PATH = "path"
    }
}
