package xyz.upperlevel.snowy.opensafe.db

import org.json.JSONObject
import java.io.File

interface DbType {
    val name: String

    fun create(root: File, name: String, password: String): Database

    fun loadFromJson(path: File, json: JSONObject): Database
}