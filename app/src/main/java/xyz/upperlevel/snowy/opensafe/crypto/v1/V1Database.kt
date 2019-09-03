package xyz.upperlevel.snowy.opensafe.crypto.v1

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.json.JSONObject
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.db.DbRegistry.Companion.DATABASE_INFO_FILE
import xyz.upperlevel.snowy.opensafe.db.DbType
import xyz.upperlevel.snowy.opensafe.db.FileInfo
import xyz.upperlevel.snowy.opensafe.db.FileSystem
import xyz.upperlevel.snowy.opensafe.util.AndroidUtil.secureRandomInstance
import xyz.upperlevel.snowy.opensafe.util.parseHex
import xyz.upperlevel.snowy.opensafe.util.toHex
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class V1Database(
    val path: File,
    val pswSalt: ByteArray,
    val pswFingerprint: ByteArray,
    private var secretKey: SecretKey? = null
) : Database {
    override fun isUnlocked(): Boolean {
        return secretKey != null
    }

    override fun getName(): String {
        return path.nameWithoutExtension
    }

    constructor(parcel: Parcel) : this(
        File(parcel.readString()),
        parcel.createByteArray()!!,
        parcel.createByteArray()!!,
        parcel.readSerializable() as SecretKey?
    )

    override fun tryUnlock(psw: String): Boolean {
        val key = generateKey(psw, pswSalt)

        if (!checkKeyFingerprint(key, pswFingerprint)) {
            return false
        }
        secretKey = key
        return true
    }

    override fun getFs(): FileSystem {
        return V1FileSystem(path, secretKey!!)
    }

    override fun delete() {
        secretKey = null
        path.deleteRecursively()
    }


    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path.path)
        parcel.writeByteArray(pswSalt)
        parcel.writeByteArray(pswFingerprint)
        parcel.writeSerializable(this.secretKey)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<V1Database> {
        val digest = MessageDigest.getInstance("SHA-256")
        val json = Json(JsonConfiguration.Stable)

        const val CIPHER_MODE = "AES/GCM/NoPadding"
        const val AES_KEY_LENGTH = 256
        const val AES_BLOCK_SIZE = 128
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 16

        override fun createFromParcel(parcel: Parcel): V1Database {
            return V1Database(parcel)
        }

        override fun newArray(size: Int): Array<V1Database?> {
            return arrayOfNulls(size)
        }

        fun sha256(data: ByteArray): ByteArray {
            digest.reset()
            return digest.digest(data)
        }

        private fun generateKey(password: String, salt: ByteArray): SecretKey {
            val iterationCount = 10000
            val keyLength = AES_KEY_LENGTH

            val keySpec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = keyFactory.generateSecret(keySpec).encoded

            return SecretKeySpec(keyBytes, "AES")
        }

        fun generateKeyFingerprint(key: SecretKey): ByteArray {
            val cipher = Cipher.getInstance(CIPHER_MODE)

            val iv = ByteArray(GCM_IV_LENGTH) // NEVER REUSE THIS IV WITH SAME KEY
            secureRandomInstance.nextBytes(iv)

            val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec)

            val data = ByteArray(AES_BLOCK_SIZE / 8)
            val encData = cipher.doFinal(data)
            val fingerprint = sha256(encData).sliceArray(0 until 4)

            return iv + fingerprint
        }

        fun checkKeyFingerprint(key: SecretKey, fingerprint: ByteArray): Boolean {
            val cipher = Cipher.getInstance(CIPHER_MODE)

            val iv = fingerprint.sliceArray(0 until GCM_IV_LENGTH)

            val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec)

            val encData = cipher.doFinal(ByteArray(AES_BLOCK_SIZE / 8))
            val genFingerprint = sha256(encData).sliceArray(0 until 4)

            return fingerprint.sliceArray(GCM_IV_LENGTH until fingerprint.size).contentEquals(genFingerprint)
        }
    }

    object TYPE : DbType {
        override val name = "v1"

        override fun create(root: File, name: String, password: String): V1Database {
            val dir = root.resolve(name)

            require(!dir.exists()) { "Database with same name exists" }

            dir.mkdir()

            val info = JSONObject()
            info.put("type", "v1")
            val salt = ByteArray(8)
            secureRandomInstance.nextBytes(salt)
            val secretKey = generateKey(password, salt)
            val fingerprint = generateKeyFingerprint(secretKey)
            info.put("password_salt", salt.toHex())
            info.put("password_fingerprint", fingerprint.toHex())
            dir.resolve(DATABASE_INFO_FILE).writeText(info.toString())

            val db = V1Database(dir, salt, fingerprint)
            db.secretKey = secretKey
            return db
        }

        override fun loadFromJson(path: File, json: JSONObject): V1Database {
            return V1Database(path, json.getString("password_salt").parseHex(), json.getString("password_fingerprint").parseHex())
        }
    }
}