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

import net.akehurst.kotlinx.filesystem.ZipFileSystem
import net.akehurst.kotlinx.filesystem.api.walkFiles

/**
 * an XML document found inside a file, possibly inside nested archives
 */
data class ExtractedDocument(
    /** where the document was found, e.g. "reports/google.zip::google.com!example.org!1763596800!1763683199.xml" */
    val location: String,
    val xml: String
)

/**
 * finds the XML documents in a file that is plain XML, gzip, tar, tar.gz or zip, in any nesting.
 * The format is decided from the content, not from the file name.
 */
class ReportExtractor(
    /** archives inside archives deeper than this are not opened */
    val maxDepth: Int = 4,
    /** the largest decompressed size accepted for one entry */
    val maxEntrySize: Int = 256 * 1024 * 1024
) {

    companion object {
        const val SEPARATOR = "::"
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        fun isZip(bytes: ByteArray): Boolean =
            bytes.size >= 4 && 'P'.code.toByte() == bytes[0] && 'K'.code.toByte() == bytes[1] && 3.toByte() == bytes[2] && 4.toByte() == bytes[3]

        fun isXml(bytes: ByteArray): Boolean {
            var p = if (bytes.size >= 3 && UTF8_BOM[0] == bytes[0] && UTF8_BOM[1] == bytes[1] && UTF8_BOM[2] == bytes[2]) 3 else 0
            while (p < bytes.size && bytes[p].toInt().toChar().isWhitespace()) p++
            return p < bytes.size && '<'.code.toByte() == bytes[p]
        }
    }

    /**
     * @return the XML documents found, and a list of problems for parts that could not be read
     */
    suspend fun extract(location: String, bytes: ByteArray): ExtractionResult {
        val documents = mutableListOf<ExtractedDocument>()
        val problems = mutableListOf<ExtractionProblem>()
        extractInto(location, bytes, 0, documents, problems)
        return ExtractionResult(documents, problems)
    }

    private suspend fun extractInto(
        location: String,
        bytes: ByteArray,
        depth: Int,
        documents: MutableList<ExtractedDocument>,
        problems: MutableList<ExtractionProblem>
    ) {
        if (depth > maxDepth) {
            problems.add(ExtractionProblem(location, "archives nested deeper than $maxDepth levels are not opened"))
            return
        }
        try {
            when {
                isXml(bytes) -> documents.add(ExtractedDocument(location, bytes.decodeToString().removePrefix("﻿")))
                Gzip.isGzip(bytes) -> {
                    val inner = Gzip.decompress(bytes, maxEntrySize)
                    extractInto(location + SEPARATOR + innerNameOfGzip(location), inner, depth + 1, documents, problems)
                }
                isZip(bytes) -> {
                    val root = ZipFileSystem.open(bytes, location)
                    for (rf in root.walkFiles()) {
                        val content = rf.file.readBytes()
                        if (null == content) {
                            problems.add(ExtractionProblem(location + SEPARATOR + rf.relativePath, "entry cannot be read"))
                        } else {
                            extractInto(location + SEPARATOR + rf.relativePath, content, depth + 1, documents, problems)
                        }
                    }
                }
                Tar.isTar(bytes) -> {
                    for (entry in Tar.entries(bytes)) {
                        extractInto(location + SEPARATOR + entry.path, entry.content, depth + 1, documents, problems)
                    }
                }
                // anything else (e.g. a README or a signature file) is not a report; it is ignored silently inside archives
                0 == depth -> problems.add(ExtractionProblem(location, "not an XML, gzip, tar or zip file"))
                else -> Unit
            }
        } catch (e: Exception) {
            problems.add(ExtractionProblem(location, e.message ?: (e::class.simpleName ?: "error")))
        }
    }

    private fun innerNameOfGzip(location: String): String {
        val name = location.substringAfterLast(SEPARATOR).substringAfterLast('/')
        return when {
            name.endsWith(".tgz", ignoreCase = true) -> name.dropLast(4) + ".tar"
            name.endsWith(".gz", ignoreCase = true) -> name.dropLast(3)
            else -> name
        }
    }
}

data class ExtractionProblem(
    val location: String,
    val message: String
)

data class ExtractionResult(
    val documents: List<ExtractedDocument>,
    val problems: List<ExtractionProblem>
)
