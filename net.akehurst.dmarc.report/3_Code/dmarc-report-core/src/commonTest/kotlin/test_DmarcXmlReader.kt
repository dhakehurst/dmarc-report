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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class test_DmarcXmlReader {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <feedback>
          <report_metadata>
            <org_name>google.com</org_name>
            <email>noreply-dmarc-support@google.com</email>
            <report_id>123</report_id>
            <date_range><begin>1763596800</begin><end>1763683199</end></date_range>
          </report_metadata>
          <policy_published><domain>example.org</domain><p>quarantine</p><pct>100</pct></policy_published>
          <record>
            <row>
              <source_ip>198.51.100.7</source_ip>
              <count>3</count>
              <policy_evaluated><disposition>quarantine</disposition><dkim>fail</dkim><spf>fail</spf></policy_evaluated>
            </row>
            <identifiers><envelope_from>bounce.example.net</envelope_from><header_from>example.org</header_from></identifiers>
            <auth_results>
              <dkim><domain>mailer.example.net</domain><result>pass</result><selector>k1</selector></dkim>
              <dkim><domain>example.org</domain><result>fail</result></dkim>
              <spf><domain>bounce.example.net</domain><scope>mfrom</scope><result>pass</result></spf>
            </auth_results>
          </record>
        </feedback>
    """.trimIndent()

    @Test
    fun read_metadata() {
        val r = DmarcXmlReader.read(xml)
        assertEquals("google.com", r.metadata.orgName)
        assertEquals("123", r.metadata.reportId)
        assertEquals(Instant.fromEpochSeconds(1763596800), r.metadata.dateBegin)
        assertEquals(Instant.fromEpochSeconds(1763683199), r.metadata.dateEnd)
        assertEquals("example.org", r.policyPublished.domain)
        assertEquals("quarantine", r.policyPublished.p)
        assertEquals(null, r.policyPublished.sp)
    }

    @Test
    fun read_record() {
        val rec = DmarcXmlReader.read(xml).records.single()
        assertEquals("198.51.100.7", rec.sourceIp)
        assertEquals(3, rec.count)
        assertEquals("quarantine", rec.policyEvaluated.disposition)
        assertEquals("fail", rec.policyEvaluated.dkim)
        assertEquals("fail", rec.policyEvaluated.spf)
        assertEquals("example.org", rec.identifiers.headerFrom)
        assertEquals("bounce.example.net", rec.identifiers.envelopeFrom)
        assertEquals(null, rec.identifiers.envelopeTo)
        assertEquals(listOf("mailer.example.net", "example.org"), rec.authResults.dkim.map { it.domain })
        assertEquals(listOf("k1", null), rec.authResults.dkim.map { it.selector })
        assertEquals("mfrom", rec.authResults.spf.single().scope)
    }

    @Test
    fun read_namespaced() {
        val ns = xml
            .replace("<feedback>", "<d:feedback xmlns:d=\"urn:ietf:params:xml:ns:dmarc-2.0\">")
            .replace("</feedback>", "</d:feedback>")
            .replace(Regex("<(/?)(?!d:|\\?)([a-z_]+)>"), "<$1d:$2>")
        val r = DmarcXmlReader.read(ns)
        assertEquals("google.com", r.metadata.orgName)
        assertEquals("198.51.100.7", r.records.single().sourceIp)
    }

    @Test
    fun not_dmarc() {
        assertFailsWith<DmarcXmlException> { DmarcXmlReader.read("<html><body/></html>") }
    }
}
