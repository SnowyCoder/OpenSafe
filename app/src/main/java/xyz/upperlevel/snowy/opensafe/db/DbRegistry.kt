package xyz.upperlevel.snowy.opensafe.db

import android.os.Parcelable
import android.util.Log
import kotlinx.android.parcel.Parcelize
import org.json.JSONObject
import xyz.upperlevel.snowy.opensafe.db.DbTypeRegistry.dbTypes
import java.io.File

@Parcelize
class DbRegistry(val root: File, val dbs: ArrayList<Database> = ArrayList()) : Parcelable {

    fun loadDb(dir: File): Database {
        val configFile = dir.resolve(DATABASE_INFO_FILE)

        if (!configFile.isFile) throw RuntimeException("Cannot find file $configFile")

        val json = JSONObject(configFile.readText())

        val dbTypeName = json.getString("type")

        val dbType = dbTypes[dbTypeName]
            ?: throw RuntimeException("Cannot find db type $dbTypeName")


        return dbType.loadFromJson(dir, json)
    }

    fun tryUnlock(psw: String): Database? {
        for (db in dbs) {
            if (!db.isUnlocked() && db.tryUnlock(psw)) {
                Log.w("DBR", "Unlock: true ${db.getName()}")
                return db
            }
        }
        Log.w("DBR", "Unlock: false with ${dbs.size} dbs")
        return null
    }

    fun unlocked(): List<Database> {
        return dbs.filter { it.isUnlocked() }
    }

    fun loadAll() {
        for (file in root.listFiles()) {
            if (!file.isDirectory) continue

            try {
                dbs.add(loadDb(file))
            } catch (e: Exception) {
                Log.w("DbRegistry", "Error loading db $file", e)
            }
        }
    }

    fun create(type: DbType, name: String, password: String): Database {
        val db = type.create(root, name, password)
        this.dbs.add(db)
        return db
    }

    fun delete(position: Int) {
        val db = this.dbs.removeAt(position)
        db.delete()
    }

    companion object {
        const val DATABASE_INFO_FILE = ".dbinfo"
    }
}