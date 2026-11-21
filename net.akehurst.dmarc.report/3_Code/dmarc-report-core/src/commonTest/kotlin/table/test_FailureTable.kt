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

    @Test
    fun only_failed_records() {
        val t = FailureTable.build(listOf(report))
        assertEquals(listOf("192.0.2.2", "192.0.2.3", "192.0.2.4"), t.rows.map { it[3] })
    }

    @Test
    fun cells() {
        val row = FailureTable.build(listOf(report)).rows.first { "192.0.2.3" == it[3] }
        assertEquals("google.com", row[0])
        assertEquals("2025-11-20 00:00", row[1])
        assertEquals("2025-11-20 23:59", row[2])
        assertEquals("example.org (old) fail, example.org (s1) pass", row[10])
        assertEquals("none", FailureTable.build(listOf(report)).rows.first { "192.0.2.4" == it[3] }[10])
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
    fun html_escapes() {
        val html = TableFormatter.html(Table(listOf("a<b"), listOf(listOf("x&y"))), "t")
        assertTrue(html.contains("a&lt;b"))
        assertTrue(html.contains("x&amp;y"))
    }
}
