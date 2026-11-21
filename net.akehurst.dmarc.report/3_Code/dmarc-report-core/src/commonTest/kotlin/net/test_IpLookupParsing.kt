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

package net.akehurst.dmarc.report.core.net

import kotlin.test.Test
import kotlin.test.assertEquals

class test_IpLookupParsing {

    @Test
    fun rdap_arin_registrant_fn_and_adr_label() {
        val json = """
            {"objectClassName":"ip network","handle":"NET-209-85-128-0-1","name":"GOOGLE",
             "entities":[{"objectClassName":"entity","handle":"GOGL","roles":["registrant"],
               "vcardArray":["vcard",[["version",{},"text","4.0"],["fn",{},"text","Google LLC"],
                 ["adr",{"label":"1600 Amphitheatre Parkway\nMountain View\nCA\n94043\nUnited States"},"text",["","","","","","",""]],
                 ["kind",{},"text","org"]]]}]}
        """.trimIndent()
        assertEquals(RdapInfo("Google LLC", "United States"), Rdap.parse(json))
    }

    @Test
    fun rdap_ripe_country_field_and_nested_registrant() {
        val json = """
            {"objectClassName":"ip network","name":"DE-TMOBILE","country":"DE",
             "entities":[{"roles":["abuse"],"vcardArray":["vcard",[["fn",{},"text","Abuse Team"]]],
               "entities":[{"roles":["registrant"],"vcardArray":["vcard",[["fn",{},"text","Telekom Deutschland GmbH"]]]}]}]}
        """.trimIndent()
        assertEquals(RdapInfo("Telekom Deutschland GmbH", "DE"), Rdap.parse(json))
    }

    @Test
    fun rdap_no_entities_uses_name() {
        assertEquals(RdapInfo("SOME-NET", null), Rdap.parse("""{"name":"SOME-NET"}"""))
    }

    @Test
    fun geo_batch() {
        val json = """
            [{"status":"success","country":"Germany","countryCode":"DE","city":"Berlin","isp":"Deutsche Telekom AG","org":"","query":"80.187.0.1"},
             {"status":"fail","message":"private range","query":"192.168.0.1"}]
        """.trimIndent()
        assertEquals(mapOf("80.187.0.1" to GeoInfo("Germany", "Berlin", "Deutsche Telekom AG")), GeoIp.parseBatch(json))
    }

    @Test
    fun geo_request_body() {
        assertEquals("""["1.2.3.4","5.6.7.8"]""", GeoIp.requestBody(listOf("1.2.3.4", "5.6.7.8")))
    }

    @Test
    fun location() {
        assertEquals("Berlin, Germany", IpInfo(null, null, null, "Germany", "Berlin").location)
        assertEquals(null, IpInfo(null, null, null, null, null).location)
    }
}
