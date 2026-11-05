package io.aperture.inspect.network.export

import java.io.Closeable
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a one-sheet Excel workbook (.xlsx) to a stream, one row at a time.
 *
 * An .xlsx file is a zip of XML parts. This writer makes only the parts Excel needs, so the
 * library does not have to carry Apache POI, which is large and does not run well on Android.
 * The rows go straight to the stream: the writer never holds more than one row.
 *
 * Strings are written inline (`t="inlineStr"`), not in a shared-strings table, because a
 * shared table must be complete before the sheet, and that would hold every body in memory.
 */
internal class XlsxWriter(
    output: OutputStream,
    private val sheetName: String,
    private val columns: List<Column>,
    /** The zone that turns a time into the date and clock time a cell shows. */
    private val timeZone: TimeZone = TimeZone.getDefault()
) : Closeable {

    data class Column(val title: String, val width: Double)

    sealed interface Cell {
        data class Text(val value: String?) : Cell
        data class Number(val value: kotlin.Number?) : Cell
        /** A moment as Unix milliseconds. The cell shows it to the millisecond. */
        data class Time(val epochMs: Long?) : Cell
    }

    private val zip = ZipOutputStream(output)
    private val out: Writer = OutputStreamWriter(zip, Charsets.UTF_8).buffered(64 * 1024)
    private var rowCount = 0
    private var closed = false

    init {
        require(columns.isNotEmpty()) { "A sheet needs at least one column" }
        part("[Content_Types].xml", CONTENT_TYPES)
        part("_rels/.rels", ROOT_RELS)
        part("xl/styles.xml", STYLES)
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        out.write(XML_HEAD)
        out.write("<worksheet xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">")
        // Keep the column names on screen while the rows scroll.
        out.write(
            "<sheetViews><sheetView workbookViewId=\"0\">" +
                "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                "</sheetView></sheetViews>"
        )
        out.write("<sheetFormatPr defaultRowHeight=\"15\"/><cols>")
        columns.forEachIndexed { i, column ->
            out.write("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${column.width}\" customWidth=\"1\"/>")
        }
        out.write("</cols><sheetData>")
        writeRow(columns.map { Cell.Text(it.title) }, header = true)
    }

    /** Add one row under the column names. It has one cell per column, in the same order. */
    fun row(cells: List<Cell>) {
        check(!closed) { "The workbook is closed" }
        require(cells.size == columns.size) { "Expected ${columns.size} cells, got ${cells.size}" }
        writeRow(cells, header = false)
    }

    private fun writeRow(cells: List<Cell>, header: Boolean) {
        rowCount += 1
        val r = rowCount
        // A body can run to hundreds of lines. A fixed height shows the first few lines of
        // each call and keeps the sheet readable as a sequence; the cell holds the rest.
        out.write(if (header) "<row r=\"$r\">" else "<row r=\"$r\" ht=\"$ROW_HEIGHT\" customHeight=\"1\">")
        cells.forEachIndexed { i, cell ->
            val ref = "${columnName(i)}$r"
            when (cell) {
                is Cell.Text -> {
                    val text = cell.value
                    if (text.isNullOrEmpty()) {
                        out.write("<c r=\"$ref\" s=\"${if (header) STYLE_HEADER else STYLE_TEXT}\"/>")
                    } else {
                        out.write("<c r=\"$ref\" s=\"${if (header) STYLE_HEADER else STYLE_TEXT}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        writeEscaped(fitCell(text))
                        out.write("</t></is></c>")
                    }
                }
                is Cell.Number -> {
                    val value = cell.value
                    if (value == null) out.write("<c r=\"$ref\" s=\"$STYLE_NUMBER\"/>")
                    else out.write("<c r=\"$ref\" s=\"$STYLE_NUMBER\"><v>$value</v></c>")
                }
                is Cell.Time -> {
                    val ms = cell.epochMs
                    if (ms == null) out.write("<c r=\"$ref\" s=\"$STYLE_TIME\"/>")
                    else out.write("<c r=\"$ref\" s=\"$STYLE_TIME\"><v>${excelSerial(ms)}</v></c>")
                }
            }
        }
        out.write("</row>")
    }

    /** Finish the sheet, add the workbook parts, and close the zip. Closes the stream too. */
    override fun close() {
        if (closed) return
        closed = true
        val range = "A1:${columnName(columns.size - 1)}$rowCount"
        out.write("</sheetData><autoFilter ref=\"$range\"/></worksheet>")
        out.flush()
        zip.closeEntry()

        val name = escapeXml(sheetName)
        val quoted = "'${sheetName.replace("'", "''")}'"
        val absolute = "$quoted!\$A\$1:\$${columnName(columns.size - 1)}\$$rowCount"
        part(
            "xl/workbook.xml",
            XML_HEAD +
                "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">" +
                "<sheets><sheet name=\"$name\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                // Excel names the filtered range itself and repairs the file when the name is missing.
                "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">" +
                escapeXml(absolute) +
                "</definedName></definedNames></workbook>"
        )
        part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        out.flush()
        zip.finish()
        zip.close()
    }

    private fun part(path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        out.write(content)
        out.flush()
        zip.closeEntry()
    }

    /** Excel counts days from 1899-12-30, in local time, with no zone. */
    private fun excelSerial(epochMs: Long): String {
        val local = epochMs + timeZone.getOffset(epochMs)
        val days = local / MS_PER_DAY.toDouble() + EXCEL_EPOCH_DAYS
        return "%.10f".format(java.util.Locale.ROOT, days)
    }

    private fun writeEscaped(text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '&' -> out.write("&amp;")
                c == '<' -> out.write("&lt;")
                c == '>' -> out.write("&gt;")
                c == '\t' || c == '\n' || c == '\r' -> out.write(c.code)
                // XML 1.0 has no form for the other control characters, and Excel calls a file
                // that holds one damaged. A body with a stray NUL or ESC is common enough.
                c < ' ' || c == '￾' || c == '￿' -> Unit
                c.isHighSurrogate() -> {
                    if (i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                        out.write(c.code)
                        out.write(text[i + 1].code)
                        i += 1
                    }
                    // A surrogate without its pair is not a character. Leave it out.
                }
                c.isLowSurrogate() -> Unit
                else -> out.write(c.code)
            }
            i += 1
        }
    }

    companion object {
        /** The most characters one Excel cell holds. */
        const val CELL_LIMIT = 32_767

        private const val ROW_HEIGHT = 60
        private const val MS_PER_DAY = 86_400_000L
        private const val EXCEL_EPOCH_DAYS = 25_569.0 // 1970-01-01 as an Excel day number

        private const val STYLE_HEADER = 1
        private const val STYLE_TEXT = 2
        private const val STYLE_TIME = 3
        private const val STYLE_NUMBER = 4

        private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

        /**
         * Text that fits one cell. A longer text is cut, and the end says so and gives the
         * full length, so nobody takes a cut body for the whole one.
         */
        fun fitCell(text: String): String {
            if (text.length <= CELL_LIMIT) return text
            val note = "\n… [Cut here. An Excel cell holds $CELL_LIMIT characters; the full text has ${text.length}.]"
            var end = CELL_LIMIT - note.length
            // Do not split a character that takes two UTF-16 units.
            if (end > 0 && text[end - 1].isHighSurrogate()) end -= 1
            return text.substring(0, end) + note
        }

        /** 0 → A, 25 → Z, 26 → AA. */
        fun columnName(index: Int): String {
            var n = index + 1
            val name = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                name.insert(0, 'A' + rem)
                n = (n - 1) / 26
            }
            return name.toString()
        }

        private fun escapeXml(text: String) = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private val CONTENT_TYPES = XML_HEAD +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

        private val ROOT_RELS = XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

        private val WORKBOOK_RELS = XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

        // Style 0 is Excel's default. 1: column names. 2: text, wrapped. 3: time to the
        // millisecond. 4: a number. Every data cell sits at the top, so a row reads across.
        private val STYLES = XML_HEAD +
            "<styleSheet xmlns=\"$MAIN_NS\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd hh:mm:ss.000\"/></numFmts>" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
            "</fonts>" +
            "<fills count=\"3\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEFEFEF\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>" +
            "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
    }
}
