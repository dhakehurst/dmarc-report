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
 * who an IP address belongs to, from its regional internet registry (RIPE, ARIN, APNIC, LACNIC, AFRINIC),
 * using RDAP (RFC 9083)
 */
data class RdapInfo(
    /** the organisation the address is registered to, or the network name if no organisation is given */
    val owner: String?,
    /** where the network is registered; a two letter code (e.g. DE) or a country name */
    val country: String?
)

object Rdap {

    /** rdap.org redirects to the registry that holds the address */
    const val BOOTSTRAP_URL = "https://rdap.org/ip/"

    /**
     * @throws IllegalArgumentException if the text is not an RDAP JSON object
     */
    fun parse(json: String): RdapInfo {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("RDAP response is not a JSON object")
        val registrant = findEntity(root, "registrant")
        val owner = registrant?.let { vcardText(it, "fn") } ?: root.string("name")
        val country = root.string("country")
            ?: registrant?.let { vcardAdrCountry(it) }
        return RdapInfo(owner?.trim()?.ifEmpty { null }, country?.trim()?.ifEmpty { null })
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** depth first search for the first entity with the role */
    private fun findEntity(obj: JsonObject, role: String): JsonObject? {
        val entities = obj["entities"] as? JsonArray ?: return null
        for (e in entities.filterIsInstance<JsonObject>()) {
            val roles = (e["roles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            if (roles.contains(role)) return e
        }
        for (e in entities.filterIsInstance<JsonObject>()) {
            findEntity(e, role)?.let { return it }
        }
        return null
    }

    /** vcardArray is ["vcard", [[name, params, type, value], ...]] (jCard, RFC 7095) */
    private fun vcardProperties(entity: JsonObject): List<JsonArray> {
        val va = entity["vcardArray"] as? JsonArray ?: return emptyList()
        val props = va.getOrNull(1) as? JsonArray ?: return emptyList()
        return props.filterIsInstance<JsonArray>()
    }

    private fun vcardText(entity: JsonObject, name: String): String? =
        vcardProperties(entity)
            .firstOrNull { name == (it.getOrNull(0) as? JsonPrimitive)?.content }
            ?.let { (it.getOrNull(3) as? JsonPrimitive)?.content }

    private fun vcardAdrCountry(entity: JsonObject): String? {
        val adr = vcardProperties(entity).firstOrNull { "adr" == (it.getOrNull(0) as? JsonPrimitive)?.content } ?: return null
        val params = adr.getOrNull(1) as? JsonObject
        (params?.get("cc") as? JsonPrimitive)?.content?.let { return it }
        // structured value: [po box, ext, street, locality, region, code, country]
        ((adr.getOrNull(3) as? JsonArray)?.getOrNull(6) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { return it }
        // otherwise the last line of the label
        return (params?.get("label") as? JsonPrimitive)?.content?.lines()?.lastOrNull { it.isNotBlank() }
    }
}
