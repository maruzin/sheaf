package com.sheaf.feature.reader.export

import org.junit.Test
import java.io.File

/**
 * Writes realistic Word and Excel exports to build/office-check/ so they can be opened in real
 * Microsoft Office before a release (the vault rule: check spreadsheet output in real Excel).
 */
class OfficeSamplesForManualCheck {
    private val pages = listOf(
        "Tenancy agreement\nLandlord: Seán Ó Briain & Co. <Ltd>\nRent: €1,250 per month \"payable\" in advance\nEmoji ok? ✅ 📄",
        "Item\t\tQty\t\tPrice\nOffice chairs  20  2400\nDesks  5  1750\nControl\u0001chars\u0007dropped",
        "x".repeat(40_000),
    )

    @Test
    fun `write office samples`() {
        val dir = File("build/office-check").apply { mkdirs() }
        File(dir, "sheaf-sample.docx").outputStream().use { OfficeWriters.writeDocx(pages, "Sheaf sample", it) }
        File(dir, "sheaf-sample.xlsx").outputStream().use { OfficeWriters.writeXlsx(pages, "Sheaf sample", it) }
        println("Office samples: " + dir.absolutePath)
    }
}
