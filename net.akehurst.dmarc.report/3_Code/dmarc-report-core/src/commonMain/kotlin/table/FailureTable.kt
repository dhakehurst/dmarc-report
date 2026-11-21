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

package net.akehurst.dmarc.report.core.table

import net.akehurst.dmarc.report.core.LoadedReport
import net.akehurst.dmarc.report.core.api.Record
import kotlin.time.Instant

/**
 * a table of strings, with a header row
 */
data class Table(
    val columns: List<String>,
    val rows: List<List<String>>
) {
    /**
     * this table without the columns that are empty in every row, to keep it narrow
     */
    fun withoutEmptyColumns(): Table {
        val keep = columns.indices.filter { c -> rows.any { it[c].isNotEmpty() } }
        return Table(keep.map { columns[it] }, rows.map { row -> keep.map { row[it] } })
    }
}

/**
 * builds the table of records that failed, i.e. those a sender needs to look at.
 * A record has failed when the DMARC DKIM or SPF verdict is not "pass",
 * or when any of its DKIM or SPF checks is not "pass".
 */
object FailureTable {

    const val PASS = "pass"

    val COLUMNS = listOf(
        "Reporter",
        "From (UTC)",
        "To (UTC)",
        "Source IP",
        "Source Host",
        "Count",
        "Header From",
        "Envelope From",
        "Envelope To",
        "DMARC DKIM",
        "DMARC SPF",
        "DKIM checks",
        "SPF checks",
        "Disposition"
    )

    /**
     * what each column means, written for the sender who reads the table
     */
    val DESCRIPTIONS = mapOf(
        "Reporter" to "the mail provider that received the messages and sent the report",
        "From (UTC)" to "start of the period the report covers",
        "To (UTC)" to "end of the period the report covers",
        "Source IP" to "IP address of the server that delivered the messages to the reporter",
        "Source Host" to "name of that server from reverse DNS; usually shows the mail service that sent the messages",
        "Count" to "number of messages from that server in the period",
        "Header From" to "domain in the From: header that the reader sees",
        "Envelope From" to "domain of the return path (MAIL FROM) used in the SMTP delivery",
        "Envelope To" to "domain the messages were addressed to",
        "DMARC DKIM" to "pass if a valid DKIM signature matched the Header From domain, else fail",
        "DMARC SPF" to "pass if SPF passed for a domain that matched the Header From domain, else fail",
        "DKIM checks" to "each DKIM signature found: signing domain (selector) and result; none = message was not signed",
        "SPF checks" to "each SPF check made: domain checked and result",
        "Disposition" to "what the reporter did: none = delivered, quarantine = spam/junk folder, reject = refused"
    )

    /**
     * the source IPs of the records that would be in the table
     */
    fun sourceIps(reports: List<LoadedReport>, includePassed: Boolean = false): Set<String> =
        reports.flatMap { lr -> lr.report.records.filter { includePassed || isFailure(it) }.map { it.sourceIp } }.toSet()

    fun isFailure(record: Record): Boolean =
        PASS != record.policyEvaluated.dkim?.lowercase()
                || PASS != record.policyEvaluated.spf?.lowercase()
                || record.authResults.dkim.any { PASS != it.result.lowercase() }
                || record.authResults.spf.any { PASS != it.result.lowercase() }

    /**
     * @param includePassed if true, records that passed are included too
     * @param hostNames the host name of each source IP, where known
     */
    fun build(reports: List<LoadedReport>, includePassed: Boolean = false, hostNames: Map<String, String> = emptyMap()): Table {
        val rows = reports
            .flatMap { lr -> lr.report.records.filter { includePassed || isFailure(it) }.map { lr to it } }
            .sortedWith(compareBy({ it.first.report.metadata.dateBegin }, { it.first.report.metadata.orgName }, { it.second.sourceIp }))
            .map { (lr, rec) ->
                val md = lr.report.metadata
                listOf(
                    md.orgName,
                    md.dateBegin.formatUtc(),
                    md.dateEnd.formatUtc(),
                    rec.sourceIp,
                    hostNames[rec.sourceIp] ?: "",
                    rec.count.toString(),
                    rec.identifiers.headerFrom ?: "",
                    rec.identifiers.envelopeFrom ?: "",
                    rec.identifiers.envelopeTo ?: "",
                    rec.policyEvaluated.dkim ?: "",
                    rec.policyEvaluated.spf ?: "",
                    rec.authResults.dkim.joinToString(", ") { d ->
                        val sel = d.selector?.let { " ($it)" } ?: ""
                        "${d.domain}$sel ${d.result}"
                    }.ifEmpty { "none" },
                    rec.authResults.spf.joinToString(", ") { "${it.domain} ${it.result}" }.ifEmpty { "none" },
                    rec.policyEvaluated.disposition ?: ""
                )
            }
        return Table(COLUMNS, rows)
    }

    /** "yyyy-MM-dd HH:mm" in UTC, or "" */
    internal fun Instant?.formatUtc(): String {
        if (null == this) return ""
        // ISO-8601, e.g. 2026-11-20T00:00:00Z
        val iso = this.toString()
        return iso.substring(0, 10) + " " + iso.substring(11, 16)
    }
}
