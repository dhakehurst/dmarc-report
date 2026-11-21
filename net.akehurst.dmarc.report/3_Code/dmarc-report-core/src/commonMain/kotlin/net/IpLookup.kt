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

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * everything found out about a source IP address; a field is null when the lookup gave nothing
 */
data class IpInfo(
    val hostName: String?,
    val owner: String?,
    val registeredCountry: String?,
    val geoCountry: String?,
    val geoCity: String?
) {
    /** "City, Country", or whichever of the two is known */
    val location: String? get() = listOfNotNull(geoCity, geoCountry).joinToString(", ").ifEmpty { null }
}

/**
 * looks up the host name (reverse DNS), registered owner and country (RDAP),
 * and approximate location (geolocation) of IP addresses.
 * A lookup that fails leaves its fields empty; it never stops the others.
 */
class IpLookup(
    val reverseDns: Boolean = true,
    val rdap: Boolean = true,
    val geo: Boolean = true,
    /** called with a message when a lookup service fails */
    val warn: (String) -> Unit = {}
) {

    suspend fun lookupAll(ips: Collection<String>): Map<String, IpInfo> = coroutineScope {
        val list = ips.filter { it.isNotEmpty() }.distinct()
        val hostsD = async { if (reverseDns) ReverseDns.lookupAll(list) else emptyMap() }
        val (rdapInfo, geoInfo) = if (rdap || geo) {
            HttpClient().use { client ->
                val r = async { if (rdap) rdapAll(client, list) else emptyMap() }
                val g = async { if (geo) geoAll(client, list) else emptyMap() }
                Pair(r.await(), g.await())
            }
        } else Pair(emptyMap(), emptyMap())
        val hosts = hostsD.await()
        list.associateWith { ip ->
            IpInfo(
                hostName = hosts[ip],
                owner = rdapInfo[ip]?.owner ?: geoInfo[ip]?.isp,
                registeredCountry = rdapInfo[ip]?.country,
                geoCountry = geoInfo[ip]?.country,
                geoCity = geoInfo[ip]?.city
            )
        }
    }

    private suspend fun rdapAll(client: HttpClient, ips: List<String>): Map<String, RdapInfo> = coroutineScope {
        // the registries rate limit, so only a few at a time
        val semaphore = Semaphore(4)
        var failures = 0
        val result = ips.map { ip ->
            async {
                semaphore.withPermit {
                    try {
                        val response = client.get(Rdap.BOOTSTRAP_URL + ip) {
                            header(HttpHeaders.Accept, "application/rdap+json, application/json")
                        }
                        if (response.status.isSuccess()) ip to Rdap.parse(response.bodyAsText()) else {
                            failures++
                            null
                        }
                    } catch (e: Exception) {
                        failures++
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().toMap()
        if (0 < failures) warn("RDAP lookup failed for $failures of ${ips.size} IPs")
        result
    }

    private suspend fun geoAll(client: HttpClient, ips: List<String>): Map<String, GeoInfo> {
        val result = mutableMapOf<String, GeoInfo>()
        for ((index, batch) in ips.chunked(GeoIp.MAX_BATCH).withIndex()) {
            // free service: at most 15 batch requests a minute
            if (0 < index) delay(4_100)
            try {
                val response = client.post(GeoIp.BATCH_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(GeoIp.requestBody(batch))
                }
                if (response.status.isSuccess()) {
                    result.putAll(GeoIp.parseBatch(response.bodyAsText()))
                } else {
                    warn("Geolocation lookup failed: HTTP ${response.status.value}")
                }
            } catch (e: Exception) {
                warn("Geolocation lookup failed: ${e.message ?: e::class.simpleName}")
            }
        }
        return result
    }
}
