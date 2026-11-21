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
 * a regular file found in a tar archive
 */
data class TarEntry(
    /** '/'-separated path inside the archive */
    val path: String,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is TarEntry && path == other.path && content.contentEquals(other.content)
    override fun hashCode(): Int = path.hashCode() * 31 + content.contentHashCode()
    override fun toString(): String = "TarEntry($path, ${content.size} bytes)"
}

/**
 * reading of (uncompressed) tar archives: ustar, GNU long names and pax path headers.
 * Only regular files are returned; directories, links and other entries are skipped.
 */
object Tar {

    private const val BLOCK = 512

    /**
     * true if the bytes start with a tar header, i.e. its checksum is valid
     */
    fun isTar(bytes: ByteArray): Boolean {
        if (bytes.size < BLOCK) return false
        val stored = parseOctal(bytes, 148, 8) ?: return false
        return stored == checksum(bytes, 0)
    }

    /**
     * @throws ArchiveException if the archive is damaged
     */
    fun entries(bytes: ByteArray): List<TarEntry> {
        val result = mutableListOf<TarEntry>()
        var pos = 0
        var longName: String? = null
        var paxPath: String? = null
        while (pos + BLOCK <= bytes.size) {
            if (isZeroBlock(bytes, pos)) break
            val stored = parseOctal(bytes, pos + 148, 8) ?: throw ArchiveException("corrupt tar header at offset $pos")
            if (stored != checksum(bytes, pos)) throw ArchiveException("tar header checksum mismatch at offset $pos")
            val size = parseOctal(bytes, pos + 124, 12) ?: throw ArchiveException("corrupt tar entry size at offset $pos")
            val type = bytes[pos + 156].toInt().toChar()
            val dataStart = pos + BLOCK
            val dataEnd = dataStart + size.toInt()
            if (size < 0 || dataEnd > bytes.size) throw ArchiveException("tar entry extends past the end of the archive at offset $pos")
            val data = bytes.copyOfRange(dataStart, dataEnd)
            when (type) {
                'L' -> longName = data.decodeToString().trimEnd('\u0000')
                'x' -> paxPath = paxValue(data, "path")
                'g' -> Unit // global pax header: nothing needed
                '0', '\u0000', '7' -> {
                    val path = paxPath ?: longName ?: headerName(bytes, pos)
                    result.add(TarEntry(path, data))
                    longName = null
                    paxPath = null
                }
                else -> {
                    longName = null
                    paxPath = null
                }
            }
            pos = dataStart + ((size.toInt() + BLOCK - 1) / BLOCK) * BLOCK
        }
        return result
    }

    private fun headerName(b: ByteArray, p: Int): String {
        val name = cString(b, p, 100)
        val isUstar = "ustar" == b.copyOfRange(p + 257, p + 262).decodeToString()
        val prefix = if (isUstar) cString(b, p + 345, 155) else ""
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun cString(b: ByteArray, p: Int, len: Int): String {
        var end = p
        while (end < p + len && 0 != b[end].toInt()) end++
        return b.copyOfRange(p, end).decodeToString()
    }

    private fun paxValue(data: ByteArray, key: String): String? {
        // records are "<len> <key>=<value>\n"
        val text = data.decodeToString()
        return text.lineSequence()
            .map { it.substringAfter(' ', "") }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
    }

    private fun parseOctal(b: ByteArray, p: Int, len: Int): Long? {
        // GNU base-256 encoding for large values
        if (0 != (b[p].toInt() and 0x80)) {
            var v = 0L
            for (i in 1 until len) v = (v shl 8) or (b[p + i].toLong() and 0xFF)
            return v
        }
        val s = cString(b, p, len).trim(' ', '\u0000')
        if (s.isEmpty()) return 0
        return s.toLongOrNull(8)
    }

    private fun checksum(b: ByteArray, p: Int): Long {
        var sum = 0L
        for (i in 0 until BLOCK) {
            sum += if (i in 148 until 156) ' '.code else (b[p + i].toInt() and 0xFF)
        }
        return sum
    }

    private fun isZeroBlock(b: ByteArray, p: Int): Boolean {
        for (i in p until p + BLOCK) if (0 != b[i].toInt()) return false
        return true
    }
}
