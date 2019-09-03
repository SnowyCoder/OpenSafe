package xyz.upperlevel.snowy.opensafe.db

import android.util.ArrayMap
import xyz.upperlevel.snowy.opensafe.crypto.v1.V1Database

object DbTypeRegistry {
    val STANDARD_DB_TYPES: Array<DbType> = arrayOf(V1Database.TYPE)
    val DEFAULT_TYPE = V1Database.TYPE

    val dbTypes = ArrayMap<String, DbType>()


    init {
        registerDbTypes(STANDARD_DB_TYPES)
    }

    fun registerDbType(dbType: DbType) {
        dbTypes[dbType.name] = dbType
    }

    fun registerDbTypes(dbTypes: Array<DbType>) {
        dbTypes.forEach { registerDbType(it) }
    }

    fun getByName(name: String): DbType? {
        return dbTypes[name]
    }
}