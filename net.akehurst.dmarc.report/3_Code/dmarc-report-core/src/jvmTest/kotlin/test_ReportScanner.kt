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

package net.akehurst.dmarc.report.core

import kotlinx.coroutines.test.runTest
import net.akehurst.kotlinx.filesystem.UserFileSystemDefault
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class test_ReportScanner {

    @Test
    fun scan_folder_with_zip_gz_and_tar_gz() = runTest {
        val path = File(this::class.java.getResource("/reports")!!.toURI()).absolutePath
        val dir = UserFileSystemDefault.getDirectory(path, FileAccessMode.READ_ONLY)
        assertNotNull(dir)

        val result = ReportScanner().scan(dir)

        assertEquals(listOf("google.com", "mail.example.com", "Outlook.com"), result.reports.map { it.report.metadata.orgName })
        assertEquals(listOf(2, 2, 1), result.reports.map { it.report.records.size })
        assertEquals(listOf("readme.txt"), result.problems.map { it.location })
        assertEquals("mail.example.com!example.org.tar.gz::mail.example.com!example.org.tar::bis.xml", result.reports[1].location)
    }
}
