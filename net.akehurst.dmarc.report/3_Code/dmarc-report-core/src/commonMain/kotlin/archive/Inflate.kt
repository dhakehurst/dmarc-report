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

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * a decoder for raw DEFLATE data (RFC 1951), written in common code so that it works on every platform.
 */
internal class Inflate(
    private val input: ByteArray,
    startOffset: Int = 0,
    private val maxOutputSize: Int = Int.MAX_VALUE
) {

    private companion object {
        const val MAX_BITS = 15

        // base lengths and extra bits for length codes 257..285
        val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)

        // base distances and extra bits for distance codes 0..29
        val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

        // order in which code length code lengths are stored
        val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

        val FIXED_LITERAL: Huffman by lazy {
            val lengths = IntArray(288)
            for (i in 0..143) lengths[i] = 8
            for (i in 144..255) lengths[i] = 9
            for (i in 256..279) lengths[i] = 7
            for (i in 280..287) lengths[i] = 8
            Huffman(lengths, lengths.size)
        }
        val FIXED_DISTANCE: Huffman by lazy { Huffman(IntArray(30) { 5 }, 30) }
    }

    /**
     * canonical Huffman decoding table, as described in RFC 1951 section 3.2.2
     */
    private class Huffman(lengths: IntArray, n: Int) {
        val count = IntArray(MAX_BITS + 1)
        val symbol = IntArray(n)

        init {
            for (i in 0 until n) count[lengths[i]]++
            count[0] = 0
            val offs = IntArray(MAX_BITS + 1)
            for (len in 1 until MAX_BITS) offs[len + 1] = offs[len] + count[len]
            for (i in 0 until n) {
                if (0 != lengths[i]) symbol[offs[lengths[i]]++] = i
            }
        }
    }

    private var pos = startOffset
    private var bitBuf = 0
    private var bitCnt = 0
    private var out = ByteArray(maxOf(1024, minOf(maxOutputSize, input.size * 4)))
    private var outLen = 0

    /** offset in [input] of the first byte after the deflate data, valid after [inflate] returns */
    val endOffset: Int get() = pos

    fun inflate(): ByteArray {
        do {
            val last = bits(1)
            when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LITERAL, FIXED_DISTANCE)
                2 -> dynamic()
                else -> throw ArchiveException("invalid deflate block type")
            }
        } while (1 != last)
        // drop the bits remaining in the current byte
        bitBuf = 0
        bitCnt = 0
        return out.copyOf(outLen)
    }

    private fun bits(need: Int): Int {
        var v = bitBuf
        while (bitCnt < need) {
            if (pos >= input.size) throw ArchiveException("unexpected end of deflate data")
            v = v or ((input[pos++].toInt() and 0xFF) shl bitCnt)
            bitCnt += 8
        }
        bitBuf = v ushr need
        bitCnt -= need
        return v and ((1 shl need) - 1)
    }

    private fun emit(b: Byte) {
        if (outLen == out.size) {
            if (out.size >= maxOutputSize) throw ArchiveException("decompressed data is larger than the limit of $maxOutputSize bytes")
            out = out.copyOf(minOf(maxOutputSize.toLong(), out.size.toLong() * 2).toInt())
        }
        out[outLen++] = b
    }

    private fun stored() {
        bitBuf = 0
        bitCnt = 0
        if (pos + 4 > input.size) throw ArchiveException("unexpected end of deflate data")
        val len = (input[pos].toInt() and 0xFF) or ((input[pos + 1].toInt() and 0xFF) shl 8)
        val nlen = (input[pos + 2].toInt() and 0xFF) or ((input[pos + 3].toInt() and 0xFF) shl 8)
        pos += 4
        if (len != (nlen.inv() and 0xFFFF)) throw ArchiveException("corrupt stored deflate block")
        if (pos + len > input.size) throw ArchiveException("unexpected end of deflate data")
        for (i in 0 until len) emit(input[pos + i])
        pos += len
    }

    private fun decode(h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..MAX_BITS) {
            code = code or bits(1)
            val count = h.count[len]
            if (code - count < first) return h.symbol[index + (code - first)]
            index += count
            first += count
            first = first shl 1
            code = code shl 1
        }
        throw ArchiveException("invalid Huffman code in deflate data")
    }

    private fun codes(lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = decode(lit)
            when {
                sym < 256 -> emit(sym.toByte())
                256 == sym -> return
                else -> {
                    val li = sym - 257
                    if (li >= 29) throw ArchiveException("invalid length code in deflate data")
                    val len = LENGTH_BASE[li] + bits(LENGTH_EXTRA[li])
                    val di = decode(dist)
                    if (di >= 30) throw ArchiveException("invalid distance code in deflate data")
                    val d = DIST_BASE[di] + bits(DIST_EXTRA[di])
                    if (d > outLen) throw ArchiveException("distance too far back in deflate data")
                    for (i in 0 until len) emit(out[outLen - d])
                }
            }
        }
    }

    private fun dynamic() {
        val nlen = bits(5) + 257
        val ndist = bits(5) + 1
        val ncode = bits(4) + 4
        if (nlen > 286 || ndist > 30) throw ArchiveException("bad counts in dynamic deflate block")
        val lengths = IntArray(320)
        for (i in 0 until ncode) lengths[CODE_LENGTH_ORDER[i]] = bits(3)
        val lencode = Huffman(lengths, 19)
        var index = 0
        while (index < nlen + ndist) {
            var sym = decode(lencode)
            if (sym < 16) {
                lengths[index++] = sym
            } else {
                var len = 0
                when (sym) {
                    16 -> {
                        if (0 == index) throw ArchiveException("repeat with no previous length in deflate data")
                        len = lengths[index - 1]
                        sym = 3 + bits(2)
                    }
                    17 -> sym = 3 + bits(3)
                    else -> sym = 11 + bits(7)
                }
                if (index + sym > nlen + ndist) throw ArchiveException("too many lengths in dynamic deflate block")
                repeat(sym) { lengths[index++] = len }
            }
        }
        if (0 == lengths[256]) throw ArchiveException("no end-of-block code in dynamic deflate block")
        val lit = Huffman(lengths.copyOfRange(0, nlen), nlen)
        val dist = Huffman(lengths.copyOfRange(nlen, nlen + ndist), ndist)
        codes(lit, dist)
    }
}
