package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvWriterTest {
    @Test fun quotesOnlyWhatNeedsIt() {
        assertEquals("plain", CsvWriter.field("plain"))
        assertEquals("\"a, b\"", CsvWriter.field("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.field("say \"hi\""))
        assertEquals("\"two\nlines\"", CsvWriter.field("two\nlines"))
    }

    @Test fun documentUsesCrlfAndAHeader() {
        val doc = CsvWriter.document(listOf("project", "rows"), listOf(listOf("Sock pair 2", "14"), listOf("Granny, square", "3")))
        assertEquals("project,rows\r\nSock pair 2,14\r\n\"Granny, square\",3\r\n", doc)
    }
}
