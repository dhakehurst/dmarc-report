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

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class test_Gzip {

    private fun gzip(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
        val bos = ByteArrayOutputStream()
        object : GZIPOutputStream(bos) {
            init {
                def.setLevel(level)
            }
        }.use { it.write(data) }
        return bos.toByteArray()
    }

    private fun sampleData(size: Int, seed: Int): ByteArray {
        val random = Random(seed)
        val words = listOf("<record>", "<row>", "pass", "fail", "192.0.2.", "example.org", "\n  ")
        val sb = StringBuilder()
        while (sb.length < size) {
            if (random.nextInt(10) < 2) sb.append(random.nextInt().toString(36)) else sb.append(words[random.nextInt(words.size)])
        }
        return sb.toString().encodeToByteArray()
    }

    @Test
    fun empty() {
        assertContentEquals(ByteArray(0), Gzip.decompress(gzip(ByteArray(0))))
    }

    @Test
    fun all_levels() {
        for (level in 0..9) {
            for (size in listOf(1, 100, 70_000, 300_000)) {
                val data = sampleData(size, level * 31 + size)
                assertContentEquals(data, Gzip.decompress(gzip(data, level)), "level $level size $size")
            }
        }
    }

    @Test
    fun random_bytes() {
        val data = Random(7).nextBytes(100_000)
        assertContentEquals(data, Gzip.decompress(gzip(data)))
    }

    @Test
    fun multiple_members() {
        val a = sampleData(5000, 1)
        val b = sampleData(3000, 2)
        assertContentEquals(a + b, Gzip.decompress(gzip(a) + gzip(b)))
    }

    @Test
    fun corrupt_crc() {
        val gz = gzip(sampleData(1000, 3))
        gz[gz.size - 6] = (gz[gz.size - 6] + 1).toByte()
        assertFailsWith<ArchiveException> { Gzip.decompress(gz) }
    }

    @Test
    fun truncated() {
        val gz = gzip(sampleData(10000, 4))
        assertFailsWith<ArchiveException> { Gzip.decompress(gz.copyOf(gz.size / 2)) }
    }

    @Test
    fun size_limit() {
        val gz = gzip(sampleData(10000, 5))
        assertFailsWith<ArchiveException> { Gzip.decompress(gz, 1000) }
    }
}
