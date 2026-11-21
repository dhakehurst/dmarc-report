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
import net.akehurst.dmarc.report.core.api.*
import net.akehurst.dmarc.report.core.net.IpInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class test_FailureTable {

    private fun record(ip: String, dkim: String, spf: String, dkimAuth: List<DkimAuthResult>, spfAuth: List<SpfAuthResult>) = Record(
        sourceIp = ip,
        count = 2,
        policyEvaluated = PolicyEvaluated("none", dkim, spf),
        identifiers = Identifiers("example.org", null, null),
        authResults = AuthResults(dkimAuth, spfAuth)
    )

    private val report = LoadedReport(
        "r.xml",
        AggregateReport(
            ReportMetadata("google.com", null, "1", Instant.fromEpochSeconds(1763596800), Instant.fromEpochSeconds(1763683199)),
            PolicyPublished("example.org", "quarantine", null, null),
            listOf(
                record("192.0.2.1", "pass", "pass", listOf(DkimAuthResult("example.org", "s1", "pass")), listOf(SpfAuthResult("example.org", null, "pass"))),
                record("192.0.2.2", "pass", "fail", listOf(DkimAuthResult("example.org", "s1", "pass")), listOf(SpfAuthResult("gmail.com", null, "softfail"))),
                record("192.0.2.3", "pass", "pass", listOf(DkimAuthResult("example.org", "old", "fail"), DkimAuthResult("example.org", "s1", "pass")), listOf(SpfAuthResult("example.org", null, "pass"))),
                record("192.0.2.4", "fail", "fail", emptyList(), listOf(SpfAuthResult("example.org", null, "fail"))),
            )
        )
    )

    private fun Table.column(name: String): List<String> = columns.indexOf(name).let { c -> rows.map { it[c] } }

    @Test
    fun only_failed_records() {
        val t = FailureTable.build(listOf(report), detailed = true)
        assertEquals(listOf("192.0.2.2", "192.0.2.3", "192.0.2.4"), t.column("Source IP"))
    }

    @Test
    fun basic_columns() {
        val t = FailureTable.build(listOf(report))
        assertEquals(FailureTable.BASIC_COLUMNS, t.columns)
        assertEquals(listOf("2025-11-20", "2025-11-20", "2025-11-20"), t.column("Date"))
        assertEquals(listOf("google.com", "google.com", "google.com"), t.column("Reporter"))
    }

    @Test
    fun detailed_cells() {
        val t = FailureTable.build(listOf(report), detailed = true)
        assertEquals(FailureTable.COLUMNS, t.columns)
        assertEquals(listOf("example.org (s1) pass", "example.org (old) fail, example.org (s1) pass", "none"), t.column("DKIM checks"))
        assertEquals(listOf("gmail.com softfail", "example.org pass", "example.org fail"), t.column("SPF checks"))
    }

    @Test
    fun ip_info() {
        val info = IpInfo("mail-sor-f41.google.com", "Google LLC", "US", "United States")
        val t = FailureTable.build(listOf(report), ipInfo = mapOf("192.0.2.2" to info), detailed = true)
        assertEquals(listOf("mail-sor-f41.google.com", "", ""), t.column("Source Host"))
        assertEquals(listOf("Google LLC", "", ""), t.column("Owner"))
        assertEquals(listOf("United States", "", ""), t.column("Country"))
    }

    @Test
    fun text_is_aligned() {
        val text = TableFormatter.text(FailureTable.build(listOf(report)))
        val lines = text.lines().filter { it.isNotEmpty() }
        assertEquals(5, lines.size)
        val firstBar = lines[0].indexOf('|')
        assertTrue(lines.all { it.indexOf('|') == firstBar || it.indexOf('+') == firstBar })
    }

    @Test
    fun withoutEmptyColumns() {
        val t = Table(listOf("a", "b", "c"), listOf(listOf("1", "", "x"), listOf("2", "", "")))
        assertEquals(Table(listOf("a", "c"), listOf(listOf("1", "x"), listOf("2", ""))), t.withoutEmptyColumns())
    }

    @Test
    fun legend_only_for_shown_columns() {
        val legend = TableFormatter.legend(Table(listOf("Source Host", "Count"), emptyList()), FailureTable.DESCRIPTIONS)
        assertTrue(legend.contains("Source Host"))
        assertTrue(!legend.contains("Disposition"))
    }

    @Test
    fun every_column_is_described() {
        assertEquals(FailureTable.COLUMNS.toSet(), FailureTable.DESCRIPTIONS.keys)
    }

    @Test
    fun withCommonColumnsMoved() {
        val t = Table(listOf("a", "b", "c"), listOf(listOf("1", "x", "p"), listOf("2", "x", "q")))
        val m = t.withCommonColumnsMoved()
        assertEquals(listOf("a", "c"), m.columns)
        assertEquals(listOf(listOf("1", "p"), listOf("2", "q")), m.rows)
        assertEquals(listOf("b" to "x"), m.common)
        assertTrue(TableFormatter.text(m).startsWith("b: x   (all rows)"))
    }

    @Test
    fun withCommonColumnsMoved_single_row_unchanged() {
        val t = Table(listOf("a"), listOf(listOf("1")))
        assertEquals(t, t.withCommonColumnsMoved())
    }

    @Test
    fun html_escapes() {
        val html = TableFormatter.html(Table(listOf("a<b"), listOf(listOf("x&y"))), "t")
        assertTrue(html.contains("a&lt;b"))
        assertTrue(html.contains("x&amp;y"))
    }
}
