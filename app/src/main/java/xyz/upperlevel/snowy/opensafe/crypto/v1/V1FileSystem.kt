package xyz.upperlevel.snowy.opensafe.crypto.v1

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StrictMode
import android.os.SystemClock.uptimeMillis
import android.util.Log
import android.util.LruCache
import kotlinx.serialization.Serializable
import xyz.upperlevel.snowy.opensafe.crypto.v1.V1Database.CREATOR.json
import xyz.upperlevel.snowy.opensafe.db.FileInfo
import xyz.upperlevel.snowy.opensafe.db.FileSystem
import xyz.upperlevel.snowy.opensafe.db.LoadData
import xyz.upperlevel.snowy.opensafe.db.LoadData.LoadRequest
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.loadThumbnail
import xyz.upperlevel.snowy.opensafe.util.InputStreamUtil.readFully
import xyz.upperlevel.snowy.opensafe.util.InputStreamUtil.skipFully
import xyz.upperlevel.snowy.opensafe.util.toHex
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.collections.ArrayList

class V1FileSystem(val path: File, val key: SecretKey) : FileSystem {
    private val infoCache = LruCache<String, FileInfo>(4 * 1024 * 1024)
    private val fileNames: ArrayList<String>


    init {
        fileNames = ArrayList(path.listFiles { _, name -> !name.startsWith(".") }
            .map { it.name }
            .sorted())
    }

    fun insertFileName(name: String) {
        val pos = fileNames.binarySearch(name)
        if (pos < 0) {
            fileNames.add(-pos-1, name)
        }
    }

    override fun getDirName(): String {
        return path.nameWithoutExtension
    }

    fun load1(file: File, options: EnumSet<LoadRequest>): LoadData {
        StrictMode.noteSlowCall("Loading and decrypting data")
        val res = LoadData()

        var info = infoCache.get(file.name)

        // Can we avoid the file access?
        if (options.contains(LoadRequest.FILE_INFO) && info != null) {
            res.info = info
            if (options.size == 1) return res
            if (options.size == 2 &&
                options.contains(LoadRequest.THUMBNAIL) &&
                info.mimeType?.startsWith("image") != true) {
                // No thumbnail available, we can return safely
                return res
            }
        }

        val dis = decrypt(file.inputStream())

        var hasThumbnail = false

        // Load metadata
        run {
            val length = ByteBuffer.wrap(dis.readFully(4)).int
            val data = ByteArray(length)
            dis.readFully(data)
            val meta = json.parse(FileMeta.serializer(), data.toString(Charsets.UTF_8))

            when (meta.type) {
                "plain" -> {}
                "image" -> hasThumbnail = true
                else -> throw IOException("Unknown metadata type " + meta.type)
            }

            if (info == null) {
                infoCache.put(file.name, meta.info)
                info = meta.info
            }
        }

        if (options.contains(LoadRequest.FILE_INFO)) {
            res.info = info
        }

        if (!options.contains(LoadRequest.THUMBNAIL) && !options.contains(LoadRequest.DATA)) {
            dis.close()
            return res
        }

        if (hasThumbnail) {
            val length = ByteBuffer.wrap(dis.readFully(4)).int
            if (options.contains(LoadRequest.THUMBNAIL)) {
                val data = ByteArray(length)
                dis.readFully(data)
                res.thumbnail = BitmapFactory.decodeByteArray(data, 0, data.size)
            } else {
                dis.skipFully(length.toLong())
            }
        }

        if (options.contains(LoadRequest.DATA)) {
            res.data = dis
        } else {
            dis.close()
        }

        return res
    }

    fun load0(file: File, options: EnumSet<LoadRequest>): LoadData {
        val startTime = uptimeMillis()
        val res = load1(file, options)
        val endTime = uptimeMillis()

        Log.e("V1", "File ${file.name} loaded in ${endTime - startTime}ms with $options")

        return res
    }


    override fun load(name: String, options: EnumSet<LoadRequest>): LoadData {
        return load0(path.resolve(getCipheredName(name)), options)
    }

    override fun load(pos: Int, options: EnumSet<LoadRequest>): LoadData {
        return load0(path.resolve(fileNames[pos]), options)
    }


    private fun saveFile(meta: FileMeta, thumbnail: ByteArray?, data: InputStream) {
        val fileMetaStr = json.stringify(FileMeta.serializer(), meta)
        val cipheredName = getCipheredName(meta.info.name)

        infoCache.put(cipheredName, meta.info)
        insertFileName(cipheredName)

        var bufferSize = fileMetaStr.length + Int.SIZE_BYTES

        thumbnail?.let {
            bufferSize += Int.SIZE_BYTES + it.size
        }

        val preDataBuffer = ByteBuffer.allocate(bufferSize)
            .putInt(fileMetaStr.length)
            .put(fileMetaStr.toByteArray())

        thumbnail?.let {
            preDataBuffer.putInt(thumbnail.size)
                .put(thumbnail)
        }

        val preDataEnc = preDataBuffer.array()

        val fullData = SequenceInputStream(
            ByteArrayInputStream(preDataEnc),
            data
        )
        val inputStream = encrypt(AndroidUtil.secureRandomInstance, fullData)
        val outputStream = path.resolve(cipheredName).outputStream()
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
    }

    fun saveFile(meta: FileMeta, thumbnail: Bitmap?, data: InputStream) {
        saveFile(
            meta,
            thumbnail?.let {
                val bos = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 50, bos)
                bos.toByteArray()
            },
            data
        )
    }

    override fun saveFile(info: FileInfo, data: InputStream, generateThumbnail: Boolean) {
        var thumbnail: Bitmap? = null
        var type = "plain"
        var stream = data

        if (generateThumbnail && info.mimeType?.startsWith("image") == true) {
            val bytes = data.readBytes()
            thumbnail = loadThumbnail(bytes, 256, 256)
            type = "image"
            stream = ByteArrayInputStream(bytes)
        }

        saveFile(FileMeta(type, info), thumbnail, stream)
    }

    override fun moveFile(fromName: String, toName: String) {
        val dis = decrypt(path.resolve(getCipheredName(fromName)).inputStream())

        val length = ByteBuffer.wrap(dis.readFully(4)).int

        val metaData = ByteArray(length)
        dis.readFully(metaData)
        val metaDataStr = String(metaData, StandardCharsets.UTF_8)
        val meta = json.parse(FileMeta.serializer(), metaDataStr)

        var thumbnail: ByteArray? = null

        when (meta.type) {
            "plain" -> {}
            "image" -> {
                val thumbLength = ByteBuffer.wrap(dis.readFully(4)).int

                thumbnail = ByteArray(thumbLength)
                dis.readFully(thumbnail)
            }
            else -> throw IOException("Unknown metadata type " + meta.type)
        }

        val newMeta = meta.copy(info=meta.info.copy(name=toName))
        saveFile(newMeta, thumbnail, dis)

        deleteFile(fromName)
    }

    override fun deleteFile(name: String): Boolean {
        val cipheredName = getCipheredName(name)
        val file = path.resolve(cipheredName)
        if (!file.exists()) return false
        infoCache.remove(cipheredName)
        check(fileNames.remove(cipheredName)) { "Cannot find file in name list" }
        return file.delete()
    }

    override fun getFileCount(): Int {
        return fileNames.size
    }

    fun getCipheredName(name: String): String {
        return V1Database.sha256(name.toByteArray()).toHex()
    }

    fun encrypt(rnd: SecureRandom, ins: InputStream): InputStream {
        val cipher = Cipher.getInstance(V1Database.CIPHER_MODE)

        val iv = ByteArray(V1Database.GCM_IV_LENGTH) // NEVER REUSE THIS IV WITH SAME KEY
        rnd.nextBytes(iv)

        val paramSpec = GCMParameterSpec(V1Database.GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec)

        return SequenceInputStream(
            ByteArrayInputStream(iv),
            CustomCipherInputStream(ins, cipher)
        )
    }

    fun decrypt(ins: InputStream): InputStream {
        val iv = ByteArray(V1Database.GCM_IV_LENGTH)
        ins.readFully(iv)

        val cipher = Cipher.getInstance(V1Database.CIPHER_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(V1Database.GCM_TAG_LENGTH * 8, iv))

        /*return if (readAll) {
            // Optimization: CipherInputStream is extremely slow as it does a lot of GC calls
            // to enhance the performance we read in memory all of the inputstream and then we
            // decipher it in one go, this can only be done with relatively small datasets
            ins.use { ByteArrayInputStream(cipher.doFinal(it.readBytes())) }
        } else {*/
        return CustomCipherInputStream(ins, cipher)
    }


    @Serializable
    data class FileMeta(val type: String, val info: FileInfo)
}