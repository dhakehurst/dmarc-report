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

package net.akehurst.dmarc.report.core.xml

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import net.akehurst.dmarc.report.core.api.*
import kotlin.time.Instant

class DmarcXmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * reads the XML of a DMARC aggregate report.
 * Elements are matched by local name, so reports that use a namespace prefix (e.g. DMARCbis) are read too.
 */
object DmarcXmlReader {

    /**
     * @throws DmarcXmlException if the text is not a DMARC aggregate report
     */
    fun read(xml: String): AggregateReport {
        val doc = try {
            Ksoup.parseXml(xml)
        } catch (e: Exception) {
            throw DmarcXmlException("not well formed XML: ${e.message}", e)
        }
        val feedback = doc.children().firstOrNull { "feedback" == it.localName() }
            ?: throw DmarcXmlException("no <feedback> root element")
        val metadataElement = feedback.child("report_metadata")
            ?: throw DmarcXmlException("no <report_metadata> element")
        val policyElement = feedback.child("policy_published")
        return AggregateReport(
            metadata = readMetadata(metadataElement),
            policyPublished = PolicyPublished(
                domain = policyElement?.childText("domain") ?: "",
                p = policyElement?.childText("p"),
                sp = policyElement?.childText("sp"),
                pct = policyElement?.childText("pct")
            ),
            records = feedback.childrenNamed("record").map { readRecord(it) }
        )
    }

    private fun readMetadata(e: Element): ReportMetadata {
        val range = e.child("date_range")
        return ReportMetadata(
            orgName = e.childText("org_name") ?: "",
            email = e.childText("email"),
            reportId = e.childText("report_id") ?: "",
            dateBegin = range?.childText("begin")?.toEpochInstant(),
            dateEnd = range?.childText("end")?.toEpochInstant()
        )
    }

    private fun readRecord(e: Element): Record {
        val row = e.child("row")
        val pe = row?.child("policy_evaluated")
        val ids = e.child("identifiers")
        val auth = e.child("auth_results")
        return Record(
            sourceIp = row?.childText("source_ip") ?: "",
            count = row?.childText("count")?.toLongOrNull() ?: 0,
            policyEvaluated = PolicyEvaluated(
                disposition = pe?.childText("disposition"),
                dkim = pe?.childText("dkim"),
                spf = pe?.childText("spf")
            ),
            identifiers = Identifiers(
                headerFrom = ids?.childText("header_from"),
                envelopeFrom = ids?.childText("envelope_from"),
                envelopeTo = ids?.childText("envelope_to")
            ),
            authResults = AuthResults(
                dkim = auth?.childrenNamed("dkim")?.map {
                    DkimAuthResult(
                        domain = it.childText("domain") ?: "",
                        selector = it.childText("selector"),
                        result = it.childText("result") ?: ""
                    )
                } ?: emptyList(),
                spf = auth?.childrenNamed("spf")?.map {
                    SpfAuthResult(
                        domain = it.childText("domain") ?: "",
                        scope = it.childText("scope"),
                        result = it.childText("result") ?: ""
                    )
                } ?: emptyList()
            )
        )
    }

    private fun Element.localName(): String = tagName().substringAfter(':')
    private fun Element.childrenNamed(name: String): List<Element> = children().filter { name == it.localName() }
    private fun Element.child(name: String): Element? = children().firstOrNull { name == it.localName() }
    private fun Element.childText(name: String): String? = child(name)?.text()?.trim()?.takeIf { it.isNotEmpty() }
    private fun String.toEpochInstant(): Instant? = toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
}
