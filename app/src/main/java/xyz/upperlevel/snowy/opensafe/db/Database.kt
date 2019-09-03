package xyz.upperlevel.snowy.opensafe.db

import android.os.Parcelable

interface Database : Parcelable {
    fun getName(): String

    fun isUnlocked(): Boolean

    fun tryUnlock(psw: String): Boolean

    fun getFs(): FileSystem

    fun delete()
}