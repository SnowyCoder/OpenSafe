package xyz.upperlevel.snowy.opensafe.fragments;

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.text.InputType
import android.text.TextUtils
import android.text.format.Formatter.formatFileSize
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_folder.*
import xyz.upperlevel.snowy.opensafe.R
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.db.FileInfo
import xyz.upperlevel.snowy.opensafe.db.FileSystem
import xyz.upperlevel.snowy.opensafe.db.LoadData
import xyz.upperlevel.snowy.opensafe.db.LoadData.LoadRequest
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.ensurePermissions
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.dp
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.guessMimeType
import xyz.upperlevel.snowy.opensafe.util.AsyncUtil.async
import xyz.upperlevel.snowy.opensafe.util.AsyncUtil.useLoadingBar
import xyz.upperlevel.snowy.opensafe.util.UriUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class FolderFragment : Fragment() {
    lateinit var listAdapter: CustomListAdapter
    var items: ArrayList<DirectoryEntry> = ArrayList()

    lateinit var db: Database
    lateinit var fs: FileSystem

    var currentPhotoPath: String? = null
    var currentExportFile: FileInfo? = null
    var currentLayoutList = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            items = savedInstanceState.getParcelableArrayList("items")!!
            currentPhotoPath = savedInstanceState.getString("currentPhotoPath")
        }

        db = arguments?.getParcelable("db")!!
        fs = db.getFs()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_folder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter = CustomListAdapter(activity!!)
        listView.adapter = listAdapter
        registerForContextMenu(listView)

        listView.setOnItemClickListener { adapterView, view, i, l ->
            if (i < 0 || i >= items.size) {
                return@setOnItemClickListener
            }
            val item = items[i]

            when (item.type) {
                CustomType.FOLDER -> Toast.makeText(context!!, "TODO: Open folder", Toast.LENGTH_LONG).show()// TODO
                CustomType.IMAGE -> showImage(item)
                CustomType.UNKNOWN -> Toast.makeText(context!!, "TODO: Open external", Toast.LENGTH_LONG).show()// TODO Intent.ACTION_VIEW
            }
        }

        if (context!!.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            addGeneric.addActionItem(
                SpeedDialActionItem.Builder(R.id.fab_take_photo, R.drawable.ic_add_photo_white)
                    .setLabel("Take photo")
                    .create()
            )
        }

        addGeneric.addActionItem(
            SpeedDialActionItem.Builder(R.id.fab_create_folder,  R.drawable.ic_add_folder_white)
                .setLabel("Add folder")
                .create()
        )

        addGeneric.addActionItem(
            SpeedDialActionItem.Builder(R.id.fab_import_file,  R.drawable.ic_add_file_white)
                .setLabel("Import file")
                .create()
        )

        addGeneric.setOnActionSelectedListener {
            when (it.id) {
                R.id.fab_take_photo -> {
                    dispatchTakePictureIntent()
                }
                R.id.fab_create_folder -> {
                    Toast.makeText(context!!, "Cannot create folder yet", Toast.LENGTH_LONG).show()
                    return@setOnActionSelectedListener false
                }
                R.id.fab_import_file -> {
                    dispatchChooseFileIntent()
                }
            }
            return@setOnActionSelectedListener false
        }

        if (items.size == 0) {
            listItemsDispatch()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_toolbar_folder, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.action_layout_flip)
        item.setIcon(when (currentLayoutList){
            true -> R.drawable.ic_view_list_black_24dp
            false -> R.drawable.ic_view_module_black_24dp
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when {
            item.itemId == R.id.action_layout_flip -> {
                flipLayout()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun flipLayout() {
        currentLayoutList = !currentLayoutList

        activity!!.invalidateOptionsMenu()
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)

        if (v == listView) {
            val info = menuInfo!! as AdapterView.AdapterContextMenuInfo

            menu.add(info.position, ENTRY_MENU_DETAILS, Menu.NONE, "Details")
            menu.add(info.position, ENTRY_MENU_RENAME, Menu.NONE, "Rename")
            menu.add(info.position, ENTRY_MENU_SHARE, Menu.NONE, "Share")
            menu.add(info.position, ENTRY_MENU_EXPORT, Menu.NONE, "Export")
            menu.add(info.position, ENTRY_MENU_DELETE, Menu.NONE, "Delete")
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val entry = items[item.groupId]
        when (item.itemId) {
            ENTRY_MENU_DETAILS -> showDetails(entry.info)
            ENTRY_MENU_RENAME -> renameFile(entry.info)
            ENTRY_MENU_SHARE -> Toast.makeText(context!!, "Share", Toast.LENGTH_LONG).show() // TODO
            ENTRY_MENU_EXPORT -> exportFile(entry.info)
            ENTRY_MENU_DELETE -> {
                async {
                    fs.deleteFile(entry.info.name)
                }.onResult {
                    listItemsDispatch()
                }.useLoadingBar(this)
            }
        }

        return super.onContextItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("items", items as ArrayList<out Parcelable>)
        currentPhotoPath?.also { outState.putString("currentPhotoPath", it) }
    }

    fun showImage(image: DirectoryEntry) {
        val args = Bundle()
        args.putParcelable(ImagePreviewFragment.PARAM_THUMBNAIL, image.thumb)
        args.putParcelable(ImagePreviewFragment.PARAM_DB, db)
        args.putString(ImagePreviewFragment.PARAM_PATH, image.info.name)

        findNavController().navigate(R.id.imagePreviewFragment, args)
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = context!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(context!!.packageManager)?.also {
                val photoFile = createImageFile()
                val photoUri = FileProvider.getUriForFile(context!!, "xyz.upperlevel.snowy.opensafe.fileprovider", photoFile)

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(takePictureIntent, TAKE_PHOTO_CODE)
            }
        }
    }

    private fun dispatchChooseFileIntent() {
        if (!ensurePermissions(
                PERMISSION_REQUIRED_IMPORT,
            "I require the read and write permission to be able to import files from an external storage",
                PERMISSION_CODE_IMPORT
        )) return

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, CHOOSE_FILE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == TAKE_PHOTO_CODE && resultCode == RESULT_OK) {
            async {
                asyncImportFile(File(currentPhotoPath), true)
            }.onResult {
                listItemsDispatch()
            }.useLoadingBar(this)
        } else if (requestCode == CHOOSE_FILE_CODE && resultCode == RESULT_OK) {
            data?.data?.also {
                async {
                    asyncImportUri(it)
                }.onResult {
                    listItemsDispatch()
                }.useLoadingBar(this)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE_IMPORT && PERMISSION_REQUIRED_IMPORT.all { checkSelfPermission(context!!, it) == PERMISSION_GRANTED }) {
            dispatchChooseFileIntent()
        } else if (requestCode == PERMISSION_CODE_EXPORT && PERMISSION_REQUIRED_EXPORT.all { checkSelfPermission(context!!, it) == PERMISSION_GRANTED }) {
            exportFile(currentExportFile!!)
            currentExportFile = null
        }
    }

    private fun chooseExportFile(info: FileInfo): File {
        info.originalPath?.also {
            val res = File(it)
            if (!res.exists()) return res
        }
        val exportRoot = Environment.getExternalStorageDirectory().resolve("export")

        exportRoot.mkdirs()

        val sameNameFile = exportRoot.resolve(info.name)
        if (!sameNameFile.exists()) return sameNameFile

        return File.createTempFile(
            info.name.substringBeforeLast("."),
            info.name.substringAfterLast(".", missingDelimiterValue = ""),
            exportRoot
        )
    }

    fun exportFile(info: FileInfo) {
        if (!ensurePermissions(
                PERMISSION_REQUIRED_EXPORT,
                "I require the write permission to be able to export files to an external storage",
                PERMISSION_CODE_EXPORT
            )) {
            currentExportFile = info
            return
        }

        async {
            val file = chooseExportFile(info)

            file.outputStream().use { output ->
                fs.getFile(info).use { input ->
                    input!!.copyTo(output)
                }
            }

            if (!fs.deleteFile(info.name)) {
                Toast.makeText(context!!, "Cannot delete exported file", Toast.LENGTH_LONG).show()
            }
            file
        }.onResult { file ->
            listItemsDispatch()

            Toast.makeText(context!!, "File saved in $file", Toast.LENGTH_LONG).show()
        }
    }

    fun renameFile(info: FileInfo) {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("New name")

        // Set up the input
        val input = EditText(context!!)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setText(info.name)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            async {
                fs.moveFile(info.name, input.text.toString())
            }.onResult {
                listItemsDispatch()
            }.useLoadingBar(this)
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    fun showDetails(info: FileInfo) {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("Details")

        // Set up the input
        val input = TextView(context!!)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.text = getString(
            R.string.file_details,
            info.name,
            info.originalPath ?: "???",
            formatFileSize(context!!, info.length),
            info.mimeType ?: "none",
            info.lastModified?.toString() ?: "???"
        )
        input.setPadding(dp(8))
        builder.setView(input)
        builder.show()
    }

    fun asyncImportFile(data: File, deletePath: Boolean = false) {
        Log.e(TAG, "Importing: $data")

        var info = FileInfo(data)

        if (deletePath) {
            info = info.copy(originalPath = null)
        }

        fs.saveFileCopy(data, info)
        try {
            data.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot delete imported file", e)
            Toast.makeText(context!!, "Cannot delete imported file", Toast.LENGTH_LONG).show()
        }
    }

    fun asyncImportUri(uri: Uri) {
        UriUtil.getPath(context!!, uri)?.also {
            asyncImportFile(File(it))
            return
        }


        val inputStream = activity!!.contentResolver.openInputStream(uri)!!

        val info = FileInfo(
            UriUtil.getFileName(activity!!, uri),
            inputStream.available().toLong(),
            uri.path,
            uri.path?.let { guessMimeType(it) },
            null
        )

        fs.saveFile(info, inputStream)

        try {
            activity!!.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot delete imported file", e)
            Toast.makeText(context!!, "Cannot delete imported file", Toast.LENGTH_LONG).show()
        }
    }



    fun listItemsDispatch() {
        activity!!.title = fs.getDirName()

        this.items.clear()

        async {
            val options = EnumSet.of(LoadRequest.FILE_INFO)
            if (SHOW_THUMBNAIL) options.add(LoadRequest.THUMBNAIL)
            (0 until fs.getFileCount()).map { DirectoryEntry.ofFile(fs.load(it, options)) }
        }.onResult {
            this.items = it as ArrayList<DirectoryEntry>
            listAdapter.notifyDataSetChanged()

            if (items.isEmpty()) {
                searchEmptyView.visibility = View.VISIBLE
                listView.visibility = View.GONE
            } else {
                searchEmptyView.visibility = View.GONE
                listView.visibility = View.VISIBLE
            }
        }.useLoadingBar(this)
    }

    @Parcelize
    class DirectoryEntry(val info: FileInfo, val type: CustomType, val ext: String?, val thumb: Bitmap?, val icon: Int?) : Parcelable {
        companion object {
            fun ofFolder(info: FileInfo): DirectoryEntry {
                return DirectoryEntry(info, CustomType.FOLDER, null, null, R.drawable.ic_folder_gray)
            }

            fun ofFile(data: LoadData): DirectoryEntry {
                val info = data.info!!
                val name = info.name
                val extSplit = name.lastIndexOf('.')

                if (extSplit == -1) {
                    return DirectoryEntry(info, CustomType.FOLDER,"", null, R.drawable.ic_file_gray)
                }

                val ext = name.substring(extSplit + 1)

                var thumb: Bitmap? = null
                var type: CustomType = CustomType.UNKNOWN

                if (info.mimeType?.startsWith("image") == true) {
                    type = CustomType.IMAGE
                    thumb = if (SHOW_THUMBNAIL) {
                        data.thumbnail
                    } else null
                }

                return DirectoryEntry(info, type, ext, thumb, R.drawable.ic_file_gray)
            }
        }
    }

    enum class CustomType {
        FOLDER, IMAGE, UNKNOWN
    }

    inner class CustomListAdapter(val context: Context) : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val resView: DocumentDetails = (convertView ?: DocumentDetails(context)) as DocumentDetails
            val item = items[position]

            resView.setContent(item.info.name, formatFileSize(context, item.info.length), item.ext, item.thumb, item.icon)

            return resView
        }

        override fun getItem(position: Int): Any {
            return items[position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getCount(): Int {
            return items.size
        }
    }

    class DocumentDetails(context: Context) : FrameLayout(context) {
        private val textView: TextView
        private val valueTextView: TextView
        private val typeTextView: TextView
        private val imageView: ImageView
        private val checkBox: CheckBox

        init {
            textView = TextView(context)
            textView.setTextColor(-0xdededf)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            textView.setLines(1)
            textView.maxLines = 1
            textView.setSingleLine(true)
            textView.gravity = Gravity.LEFT
            addView(textView)
            var layoutParams = textView.layoutParams as LayoutParams
            layoutParams.width = LayoutParams.WRAP_CONTENT
            layoutParams.height = LayoutParams.WRAP_CONTENT
            layoutParams.topMargin = dp(10)
            layoutParams.leftMargin = dp(71)
            layoutParams.rightMargin = dp(16)
            layoutParams.gravity = Gravity.LEFT
            textView.layoutParams = layoutParams

            valueTextView = TextView(context)
            valueTextView.setTextColor(-0x757576)
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            valueTextView.setLines(1)
            valueTextView.maxLines = 1
            valueTextView.setSingleLine(true)
            valueTextView.gravity = Gravity.LEFT
            addView(valueTextView)
            layoutParams = valueTextView.layoutParams as LayoutParams
            layoutParams.width = LayoutParams.WRAP_CONTENT
            layoutParams.height = LayoutParams.WRAP_CONTENT
            layoutParams.topMargin = dp(35)
            layoutParams.leftMargin = dp(71)
            layoutParams.rightMargin = dp(16)
            layoutParams.gravity = Gravity.LEFT
            valueTextView.layoutParams = layoutParams

            typeTextView = TextView(context)
            typeTextView.setBackgroundColor(-0x8a8a8b)
            typeTextView.ellipsize = TextUtils.TruncateAt.MARQUEE
            typeTextView.gravity = Gravity.CENTER
            typeTextView.setSingleLine(true)
            typeTextView.setTextColor(-0x2e2e2f)
            typeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            typeTextView.typeface = Typeface.DEFAULT_BOLD
            addView(typeTextView)
            layoutParams = typeTextView.layoutParams as LayoutParams
            layoutParams.width = dp(40)
            layoutParams.height = dp(40)
            layoutParams.leftMargin = dp(16)
            layoutParams.rightMargin = dp(0)
            layoutParams.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            typeTextView.layoutParams = layoutParams

            imageView = ImageView(context)
            addView(imageView)
            layoutParams = imageView.layoutParams as LayoutParams
            layoutParams.width = dp(40)
            layoutParams.height = dp(40)
            layoutParams.leftMargin = dp(16)
            layoutParams.rightMargin = dp(0)
            layoutParams.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            imageView.setLayoutParams(layoutParams)

            checkBox = CheckBox(context)
            checkBox.visibility = View.GONE
            addView(checkBox)
            layoutParams = checkBox.layoutParams as FrameLayout.LayoutParams
            layoutParams.width = dp(22)
            layoutParams.height = dp(22)
            layoutParams.topMargin = dp(34)
            layoutParams.leftMargin = dp(38)
            layoutParams.rightMargin = 0
            layoutParams.gravity = Gravity.LEFT
            checkBox.layoutParams = layoutParams
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY)
            )
        }

        fun setContent(text: String, value: String, type: String?, thumb: Bitmap?, resId: Int?) {
            textView.text = text
            valueTextView.text = value
            if (type != null) {
                typeTextView.visibility = View.VISIBLE
                typeTextView.text = type
            } else {
                typeTextView.visibility = View.GONE
            }
            if (thumb != null || resId != null) {
                if (thumb != null) {
                    imageView.setImageBitmap(thumb)
                } else {
                    imageView.setImageResource(resId!!)
                }
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
        }

        fun setChecked(checked: Boolean, animated: Boolean) {
            if (checkBox.visibility != View.VISIBLE) {
                checkBox.visibility = View.VISIBLE
            }
            checkBox.isChecked = checked
        }
    }

    companion object {
        const val TAG = "FolderFragment"
        const val SHOW_THUMBNAIL = true

        const val ENTRY_MENU_DETAILS = 0
        const val ENTRY_MENU_RENAME = 1
        const val ENTRY_MENU_SHARE = 2
        const val ENTRY_MENU_EXPORT = 3
        const val ENTRY_MENU_DELETE = 4

        val PERMISSION_REQUIRED_IMPORT = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val PERMISSION_REQUIRED_EXPORT = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        const val PERMISSION_CODE_IMPORT = 1
        const val PERMISSION_CODE_EXPORT = 2


        const val TAKE_PHOTO_CODE = 0
        const val CHOOSE_FILE_CODE = 1
    }
}