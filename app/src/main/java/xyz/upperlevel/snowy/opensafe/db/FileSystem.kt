package xyz.upperlevel.snowy.opensafe.db


import xyz.upperlevel.snowy.opensafe.db.LoadData.LoadRequest
import java.io.File
import java.io.InputStream
import java.util.*

interface FileSystem {
    fun getDirName(): String

    fun getFileCount(): Int

    fun load(pos: Int, options: EnumSet<LoadRequest>): LoadData

    fun load(name: String, options: EnumSet<LoadRequest>): LoadData

    fun saveFile(info: FileInfo, data: InputStream, generateThumbnail: Boolean = true)

    fun deleteFile(name: String): Boolean

    fun getFileDetails(name: String): FileInfo? {
        return load(name, EnumSet.of(LoadRequest.FILE_INFO)).info!!
    }

    fun getFile(name: String): InputStream? {
        val options = EnumSet.of(LoadRequest.DATA)
        return load(name, options).data!!
    }

    fun getFile(info: FileInfo): InputStream? {
        return getFile(info.name)
    }


    fun moveFile(fromName: String, toName: String) {
        val data = load(fromName, EnumSet.of(LoadRequest.FILE_INFO, LoadRequest.DATA))
        data.data!!.use {
            saveFile(data.info!!.copy(name = toName), it)
        }
        deleteFile(fromName)
    }

    fun saveFileCopy(file: File, info: FileInfo? = null) {
        file.inputStream().use { saveFile(info ?: FileInfo(file), it) }
    }

    /*fun getFolderList(): List<String>

    fun getFolder(name: String): FileSystem?*/
}