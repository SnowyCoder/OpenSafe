package xyz.upperlevel.snowy.opensafe.util

import kotlinx.io.InputStream
import java.io.EOFException

object InputStreamUtil {
    private val SKIP_BUFFER = ByteArray(512)

    fun InputStream.readFully(ba: ByteArray): ByteArray {
        var off = 0
        var len = ba.size

        while (len > 0) {
            val read = read(ba, off, len)
            if (read < 0) throw EOFException()
            off += read
            len -= read
        }

        return ba
    }

    fun InputStream.readFully(len: Int): ByteArray = readFully(ByteArray(len))

    fun InputStream.skipFully(len: Long) {
        var remaining = len

        while (remaining > 0) {
            if (available() > 0) {
                val read = skip(remaining)
                remaining -= read
                if (read != 0L) continue
            }
            val readX = if (SKIP_BUFFER.size > remaining) remaining.toInt() else SKIP_BUFFER.size
            val read = read(SKIP_BUFFER, 0, readX)
            if (read < 0) throw EOFException()
            remaining -= read
        }
    }
}