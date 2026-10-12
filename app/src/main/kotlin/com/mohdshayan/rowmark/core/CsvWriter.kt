package com.mohdshayan.rowmark.core

/** RFC 4180: CRLF line ends, fields quoted when they hold a comma, a quote or a line break. */
object CsvWriter {
    fun field(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun line(values: List<String>): String = values.joinToString(",") { field(it) }

    fun document(header: List<String>, rows: List<List<String>>): String =
        buildString {
            append(line(header)).append("\r\n")
            for (r in rows) append(line(r)).append("\r\n")
        }
}
