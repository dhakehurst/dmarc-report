/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.dmarc.report.core.archive

/**
 * decoding of gzip (RFC 1952) files.
 * A file may hold several gzip members one after another; their content is joined.
 */
object Gzip {

    private const val ID1 = 0x1F
    private const val ID2 = 0x8B
    private const val CM_DEFLATE = 8
    private const val FHCRC = 0x02
    private const val FEXTRA = 0x04
    private const val FNAME = 0x08
    private const val FCOMMENT = 0x10

    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && ID1 == (bytes[0].toInt() and 0xFF) && ID2 == (bytes[1].toInt() and 0xFF)

    /**
     * @throws ArchiveException if the data is not gzip, is damaged, or decompresses to more than [maxSize] bytes
     */
    fun decompress(bytes: ByteArray, maxSize: Int = Int.MAX_VALUE): ByteArray {
        if (!isGzip(bytes)) throw ArchiveException("not gzip data")
        val result = mutableListOf<ByteArray>()
        var pos = 0
        while (pos < bytes.size && isGzip(bytes.copyOfRange(pos, minOf(pos + 2, bytes.size)))) {
            if (pos + 10 > bytes.size) throw ArchiveException("truncated gzip header")
            if (CM_DEFLATE != (bytes[pos + 2].toInt() and 0xFF)) throw ArchiveException("unsupported gzip compression method")
            val flags = bytes[pos + 3].toInt() and 0xFF
            pos += 10
            if (0 != (flags and FEXTRA)) {
                if (pos + 2 > bytes.size) throw ArchiveException("truncated gzip header")
                val xlen = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                pos += 2 + xlen
            }
            if (0 != (flags and FNAME)) pos = skipZeroTerminated(bytes, pos)
            if (0 != (flags and FCOMMENT)) pos = skipZeroTerminated(bytes, pos)
            if (0 != (flags and FHCRC)) pos += 2
            if (pos > bytes.size) throw ArchiveException("truncated gzip header")
            val already = result.sumOf { it.size }
            val inflate = Inflate(bytes, pos, maxSize - already)
            val content = inflate.inflate()
            pos = inflate.endOffset
            if (pos + 8 > bytes.size) throw ArchiveException("truncated gzip trailer")
            val crc = readIntLE(bytes, pos).toLong() and 0xFFFFFFFFL
            val isize = readIntLE(bytes, pos + 4).toLong() and 0xFFFFFFFFL
            pos += 8
            if (crc != Crc32.of(content)) throw ArchiveException("gzip CRC-32 does not match the content")
            if (isize != (content.size.toLong() and 0xFFFFFFFFL)) throw ArchiveException("gzip size does not match the content")
            result.add(content)
        }
        return if (1 == result.size) result[0] else {
            val all = ByteArray(result.sumOf { it.size })
            var o = 0
            for (r in result) {
                r.copyInto(all, o)
                o += r.size
            }
            all
        }
    }

    private fun skipZeroTerminated(bytes: ByteArray, start: Int): Int {
        var p = start
        while (p < bytes.size && 0 != bytes[p].toInt()) p++
        return p + 1
    }

    private fun readIntLE(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
}

internal object Crc32 {
    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (0 != (c and 1)) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    fun of(bytes: ByteArray): Long {
        var c = -1
        for (b in bytes) c = TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv().toLong() and 0xFFFFFFFFL
    }
}
