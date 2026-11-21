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

package net.akehurst.dmarc.report.cli

import net.akehurst.dmarc.report.core.ReportScanner
import net.akehurst.dmarc.report.dmarc.report.cli.BuildConfig
import net.akehurst.dmarc.report.core.net.IpLookup
import net.akehurst.dmarc.report.core.table.FailureTable
import net.akehurst.dmarc.report.core.table.TableFormatter
import net.akehurst.kotlinx.filesystem.UserFileSystemDefault
import net.akehurst.kotlinx.filesystem.api.FileAccessMode

/**
 * the command line application: reads the arguments, scans the folder and writes the table
 */
class DmarcReportApplication(
    val out: (String) -> Unit,
    val err: (String) -> Unit
) {

    companion object {
        const val USAGE = """Usage: dmarc-report [options] <folder>

Reads every DMARC aggregate report (.xml, .gz, .zip, .tar, .tar.gz) in <folder> and its
sub-folders, and prints a table of the records where DKIM or SPF failed, with the
columns that help a sender recognise the message.
Columns that are empty in every row are left out, and a column with the same value
in every row is printed once above the table instead.

Options:
  --html <file>   also write the table as an HTML page, to open in a browser and paste into an email
  --details       show every column, including the technical ones (source IP, envelope from,
                  DKIM and SPF results) needed to work out why a message failed
  --all           include records that passed as well
  --no-lookup     do not look up the source IPs (reverse DNS, owner, country); no network needed
  --no-legend     do not print the explanation of the columns after the table
  --version       print the version
  --help          print this help"""
    }

    data class Options(
        val folder: String,
        val htmlFile: String?,
        val all: Boolean,
        val noLegend: Boolean,
        val noLookup: Boolean,
        val details: Boolean
    )

    /**
     * @return the process exit code
     */
    suspend fun run(args: Array<String>): Int {
        val options = parse(args) ?: return 2
        val dir = UserFileSystemDefault.getDirectory(options.folder, FileAccessMode.READ_ONLY)
        if (null == dir || !dir.exists()) {
            err("Folder not found: ${options.folder}")
            return 1
        }
        val scan = ReportScanner().scan(dir)
        val ipInfo = if (options.noLookup) emptyMap() else {
            val ips = FailureTable.sourceIps(scan.reports, options.all)
            err("Looking up ${ips.size} source IPs (reverse DNS, RDAP, ip-api.com)...")
            IpLookup(warn = err).lookupAll(ips)
        }
        val table = FailureTable.build(scan.reports, options.all, ipInfo, options.details).withoutEmptyColumns().withCommonColumnsMoved()

        out(TableFormatter.text(table))
        if (!options.noLegend) out(TableFormatter.legend(table, FailureTable.DESCRIPTIONS))
        err("${scan.reports.size} reports read, ${table.rows.size} ${if (options.all) "records" else "records with a failure"}.")
        for (p in scan.problems) err("Skipped ${p.location}: ${p.message}")

        if (null != options.htmlFile) {
            val title = if (options.all) "DMARC records" else "DMARC records with a DKIM or SPF failure"
            if (writeFile(options.htmlFile, TableFormatter.html(table, title, if (options.noLegend) emptyMap() else FailureTable.DESCRIPTIONS))) {
                err("HTML written to ${options.htmlFile}")
            } else {
                err("Cannot write ${options.htmlFile}")
                return 1
            }
        }
        return 0
    }

    private fun parse(args: Array<String>): Options? {
        var folder: String? = null
        var html: String? = null
        var all = false
        var noLegend = false
        var noLookup = false
        var details = false
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "--help", "-h" -> {
                    out(USAGE)
                    return null
                }
                "--version" -> {
                    out("dmarc-report ${BuildConfig.version} (${BuildConfig.buildDate})")
                    return null
                }
                "--all" -> all = true
                "--no-legend" -> noLegend = true
                "--no-lookup" -> noLookup = true
                "--details" -> details = true
                "--html" -> {
                    i++
                    if (i >= args.size) return usageError("--html needs a file name")
                    html = args[i]
                }
                else -> when {
                    a.startsWith("-") -> return usageError("Unknown option: $a")
                    null != folder -> return usageError("Only one folder can be given")
                    else -> folder = a
                }
            }
            i++
        }
        if (null == folder) return usageError("No folder given")
        return Options(folder, html, all, noLegend, noLookup, details)
    }

    private fun usageError(message: String): Options? {
        err(message)
        err(USAGE)
        return null
    }

    private suspend fun writeFile(path: String, content: String): Boolean {
        val sep = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        val parentPath = if (sep < 0) "." else path.substring(0, sep).ifEmpty { "/" }
        val name = path.substring(sep + 1)
        val parent = UserFileSystemDefault.getDirectory(parentPath, FileAccessMode.READ_WRITE) ?: return false
        val file = parent.getOrCreateFile(name) ?: return false
        file.writeContent(content)
        return true
    }
}
