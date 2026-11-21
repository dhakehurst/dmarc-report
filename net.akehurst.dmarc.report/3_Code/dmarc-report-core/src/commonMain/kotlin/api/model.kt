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

package net.akehurst.dmarc.report.core.api

import kotlin.time.Instant

/**
 * a DMARC aggregate report, as defined in RFC 7489 Appendix C.
 * Only the parts needed by this application are kept; unknown elements are ignored.
 */
data class AggregateReport(
    val metadata: ReportMetadata,
    val policyPublished: PolicyPublished,
    val records: List<Record>
)

data class ReportMetadata(
    val orgName: String,
    val email: String?,
    val reportId: String,
    val dateBegin: Instant?,
    val dateEnd: Instant?
)

data class PolicyPublished(
    val domain: String,
    val p: String?,
    val sp: String?,
    val pct: String?
)

data class Record(
    val sourceIp: String,
    val count: Long,
    val policyEvaluated: PolicyEvaluated,
    val identifiers: Identifiers,
    val authResults: AuthResults
)

data class PolicyEvaluated(
    /** none, quarantine or reject */
    val disposition: String?,
    /** DKIM result after alignment: pass or fail */
    val dkim: String?,
    /** SPF result after alignment: pass or fail */
    val spf: String?
)

data class Identifiers(
    val headerFrom: String?,
    val envelopeFrom: String?,
    val envelopeTo: String?
)

data class AuthResults(
    val dkim: List<DkimAuthResult>,
    val spf: List<SpfAuthResult>
)

data class DkimAuthResult(
    val domain: String,
    val selector: String?,
    val result: String
)

data class SpfAuthResult(
    val domain: String,
    val scope: String?,
    val result: String
)
