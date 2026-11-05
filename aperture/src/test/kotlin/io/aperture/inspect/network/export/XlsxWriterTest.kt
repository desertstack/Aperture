package io.aperture.inspect.network.export

import io.aperture.inspect.network.export.XlsxWriter.Cell
import io.aperture.inspect.network.export.XlsxWriter.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Excel opens a damaged workbook only after a "repair" dialog, and it drops what it cannot
 * read. These tests hold the file to the parts Excel needs and to well-formed XML.
 */
class XlsxWriterTest {

    private val columns = listOf(Column("Name", 10.0), Column("When", 20.0), Column("Count", 8.0))

    private fun write(vararg rows: List<Cell>): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        XlsxWriter(bytes, "Calls", columns, TimeZone.getTimeZone("UTC")).use { sheet ->
            rows.forEach { sheet.row(it) }
        }
        val parts = mutableMapOf<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().decodeToString()
            }
        }
        return parts
    }

    private fun parses(xml: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    }

    @Test
    fun `the workbook has every part Excel needs and each one parses`() {
        val parts = write(listOf(Cell.Text("a"), Cell.Time(0L), Cell.Number(3)))

        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"
            ),
            parts.keys
        )
        parts.values.forEach(::parses)
    }

    @Test
    fun `the column names are row one and the filter covers every row`() {
        val sheet = write(
            listOf(Cell.Text("a"), Cell.Time(null), Cell.Number(null)),
            listOf(Cell.Text("b"), Cell.Time(null), Cell.Number(null))
        )["xl/worksheets/sheet1.xml"]!!

        assertTrue(sheet.contains("<row r=\"1\"><c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Name</t>"))
        assertTrue(sheet.contains("<autoFilter ref=\"A1:C3\"/>"))
        assertTrue(sheet.contains("state=\"frozen\""))
    }

    @Test
    fun `a time is an Excel day number to the millisecond`() {
        // 1970-01-01 00:00:00.500 UTC is day 25569 and half a second.
        val sheet = write(listOf(Cell.Text(null), Cell.Time(500L), Cell.Number(null)))["xl/worksheets/sheet1.xml"]!!

        val value = Regex("<c r=\"B2\" s=\"3\"><v>([^<]+)</v>").find(sheet)!!.groupValues[1].toDouble()
        assertEquals(25569.0 + 0.5 / 86_400.0, value, 1e-9)
    }

    @Test
    fun `markup and control characters in a body cannot break the sheet`() {
        val body = "<html> & \"quotes\" \u0000nul \u001Besc \uD800lone tab\tline\nend 😀"
        val sheet = write(listOf(Cell.Text(body), Cell.Time(null), Cell.Number(null)))["xl/worksheets/sheet1.xml"]!!

        parses(sheet)
        assertTrue(sheet.contains("&lt;html&gt; &amp; \"quotes\" nul esc lone tab\tline\nend 😀"))
        assertFalse(sheet.contains('\u0000'))
        assertFalse(sheet.contains('\u001B'))
    }

    @Test
    fun `a text longer than a cell is cut and says so`() {
        val long = "x".repeat(40_000)

        val fitted = XlsxWriter.fitCell(long)

        assertEquals(XlsxWriter.CELL_LIMIT, fitted.length)
        assertTrue(fitted.endsWith("the full text has 40000.]"))
        assertEquals("short", XlsxWriter.fitCell("short"))
    }

    @Test
    fun `columns are named the way Excel names them`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("R", XlsxWriter.columnName(17))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
    }
}
