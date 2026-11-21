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

import kotlinx.serialization.json.*

/**
 * the approximate physical location of an IP address, from a geolocation database
 */
data class GeoInfo(
    val country: String?,
    val city: String?,
    /** the internet provider or organisation the geolocation service names */
    val isp: String?
)

/**
 * geolocation using the free batch service of ip-api.com.
 * The free service is only available over plain http, allows 15 batch requests a minute,
 * and up to 100 addresses per request.
 */
object GeoIp {

    const val BATCH_URL = "http://ip-api.com/batch?fields=status,message,country,countryCode,city,isp,org,query"
    const val MAX_BATCH = 100

    fun requestBody(ips: List<String>): String = JsonArray(ips.map { JsonPrimitive(it) }).toString()

    /**
     * @return the location of each address that the service could locate
     * @throws IllegalArgumentException if the text is not a JSON array
     */
    fun parseBatch(json: String): Map<String, GeoInfo> {
        val arr = Json.parseToJsonElement(json) as? JsonArray ?: throw IllegalArgumentException("geolocation response is not a JSON array")
        return arr.filterIsInstance<JsonObject>()
            .filter { "success" == it.string("status") }
            .mapNotNull { o ->
                val ip = o.string("query") ?: return@mapNotNull null
                ip to GeoInfo(
                    country = o.string("country")?.ifEmpty { null },
                    city = o.string("city")?.ifEmpty { null },
                    isp = (o.string("org")?.ifEmpty { null } ?: o.string("isp"))?.ifEmpty { null }
                )
            }.toMap()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
