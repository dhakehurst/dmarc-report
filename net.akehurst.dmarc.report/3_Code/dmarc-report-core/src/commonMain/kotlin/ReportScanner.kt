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

package net.akehurst.dmarc.report.core

import net.akehurst.dmarc.report.core.api.AggregateReport
import net.akehurst.dmarc.report.core.archive.ExtractionProblem
import net.akehurst.dmarc.report.core.archive.ReportExtractor
import net.akehurst.dmarc.report.core.xml.DmarcXmlException
import net.akehurst.dmarc.report.core.xml.DmarcXmlReader
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.walkFiles

/**
 * a report read from a file, with where it was found
 */
data class LoadedReport(
    val location: String,
    val report: AggregateReport
)

data class ScanResult(
    val reports: List<LoadedReport>,
    /** files, or parts of files, that could not be read as a DMARC report */
    val problems: List<ExtractionProblem>
)

/**
 * reads every DMARC aggregate report in a directory and its sub-directories.
 * A report that appears more than once (same reporting org and report id) is kept once.
 */
class ReportScanner(
    val extractor: ReportExtractor = ReportExtractor()
) {

    suspend fun scan(directory: DirectoryHandle): ScanResult {
        val reports = mutableListOf<LoadedReport>()
        val problems = mutableListOf<ExtractionProblem>()
        val seen = mutableSetOf<Pair<String, String>>()
        for (rf in directory.walkFiles().sortedBy { it.relativePath }) {
            val bytes = rf.file.readBytes()
            if (null == bytes) {
                problems.add(ExtractionProblem(rf.relativePath, "file cannot be read"))
                continue
            }
            val extracted = extractor.extract(rf.relativePath, bytes)
            problems.addAll(extracted.problems)
            for (doc in extracted.documents) {
                try {
                    val report = DmarcXmlReader.read(doc.xml)
                    val key = Pair(report.metadata.orgName, report.metadata.reportId)
                    if (report.metadata.reportId.isEmpty() || seen.add(key)) {
                        reports.add(LoadedReport(doc.location, report))
                    }
                } catch (e: DmarcXmlException) {
                    problems.add(ExtractionProblem(doc.location, e.message ?: "not a DMARC aggregate report"))
                }
            }
        }
        return ScanResult(reports, problems)
    }
}
