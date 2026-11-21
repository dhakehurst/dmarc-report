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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * the host name that the reverse DNS (PTR) record of this IP address gives,
 * or null if there is none, the lookup fails, or the platform cannot do lookups
 */
expect suspend fun reverseDnsLookup(ip: String): String?

object ReverseDns {

    /**
     * look up all the given addresses, at most [parallel] at a time
     * @return the host name of each address that has one
     */
    suspend fun lookupAll(ips: Collection<String>, parallel: Int = 16): Map<String, String> = coroutineScope {
        val semaphore = Semaphore(parallel)
        ips.distinct()
            .filter { it.isNotEmpty() }
            .map { ip -> async { semaphore.withPermit { ip to reverseDnsLookup(ip) } } }
            .awaitAll()
            .mapNotNull { (ip, host) -> host?.let { ip to it } }
            .toMap()
    }
}
