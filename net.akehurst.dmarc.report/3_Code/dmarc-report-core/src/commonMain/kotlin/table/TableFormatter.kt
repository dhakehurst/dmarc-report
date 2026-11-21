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

/**
 * writes a [Table] in formats that can be pasted into an email
 */
object TableFormatter {

    /**
     * a table aligned with spaces, for a plain text or monospaced email
     */
    fun text(table: Table): String {
        val widths = table.columns.indices.map { c ->
            maxOf(table.columns[c].length, table.rows.maxOfOrNull { it[c].length } ?: 0)
        }
        fun line(cells: List<String>) = cells.mapIndexed { c, s -> s.padEnd(widths[c]) }.joinToString(" | ").trimEnd()
        val sb = StringBuilder()
        sb.appendLine(line(table.columns))
        sb.appendLine(widths.joinToString("-+-") { "-".repeat(it) })
        for (row in table.rows) sb.appendLine(line(row))
        return sb.toString()
    }

    /**
     * a self-contained HTML page; open it in a browser, select the table and paste it into an email
     */
    fun html(table: Table, title: String): String {
        val td = "style=\"border:1px solid #999;padding:2px 6px;font-family:Arial,sans-serif;font-size:12px;white-space:nowrap\""
        val th = "style=\"border:1px solid #999;padding:2px 6px;font-family:Arial,sans-serif;font-size:12px;background:#eee;text-align:left\""
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html><head><meta charset=\"utf-8\"><title>${escape(title)}</title></head><body>")
        sb.appendLine("<h3 style=\"font-family:Arial,sans-serif\">${escape(title)}</h3>")
        sb.appendLine("<table style=\"border-collapse:collapse\">")
        sb.appendLine("<tr>" + table.columns.joinToString("") { "<th $th>${escape(it)}</th>" } + "</tr>")
        for (row in table.rows) {
            sb.appendLine("<tr>" + row.joinToString("") { "<td $td>${escape(it)}</td>" } + "</tr>")
        }
        sb.appendLine("</table>")
        sb.appendLine("</body></html>")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
