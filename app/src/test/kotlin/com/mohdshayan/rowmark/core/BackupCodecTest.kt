package com.mohdshayan.rowmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val file = BackupFile(
        exportedAt = 1_700_000_000_000L,
        projects = listOf(ProjectDto("u1", "Ribbed raglan", "knitting", "active", "4 mm", "Malabrigo", "notes", 120, 42, null, 1L, 2L, 3L)),
        counters = listOf(CounterDto(1, "u1", "Row", "ROW", 6, 18, 2, false, 6, "Cable cross", 0), CounterDto(2, "u1", "Repeat", "REPEAT", 3, 8, null, false, null, "", 1)),
        events = listOf(EventDto("u1", 1, 1, 1, 6, "TAP", 5L, 1)),
        sessions = listOf(SessionDto("u1", 10L, 20L, 10L, 6)),
        schedules = listOf(ScheduleDto("u1", null, "increase", "Raglan", 12, 6, 8)),
        patterns = listOf(PatternDto("p1", "u1", "PDF", "abc.pdf", "Chart", 3, true, 1, 0.4f, 0.05f)),
        markup = listOf(MarkupDto("p1", 1, "highlighter", "[[0.1,0.2],[0.3,0.4]]")),
        yarns = listOf(YarnDto("y1", "Malabrigo", "Rios", "Teal Feather", "worsted", "merino", 192, 4f, "A12", null)),
        tools = listOf(ToolDto("t1", "circular", 4f, 2)),
        projectYarns = listOf(ProjectYarnDto("u1", "y1", 1.5f)),
        settings = mapOf("theme" to "DARK"),
    )

    @Test fun roundTrip() {
        val text = BackupCodec.encode(file)
        assertEquals(file, BackupCodec.decode(text))
    }

    @Test fun rejectsFilesWithoutAFormatOrFromAnotherApp() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"app\":\"rowmark\"}") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"format\":1,\"app\":\"other\",\"exportedAt\":1}") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json at all") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"format\":99,\"app\":\"rowmark\",\"exportedAt\":1}") }
    }

    @Test fun unknownKeysAreIgnoredSoOlderBuildsCanReadNewerMinorFields() {
        val text = "{\"format\":1,\"app\":\"rowmark\",\"exportedAt\":5,\"extra\":true}"
        assertEquals(5L, BackupCodec.decode(text).exportedAt)
    }

    @Test fun mergePlanSplitsNewFromReplaced() {
        val plan = BackupCodec.plan(setOf("u1", "u9"), file, RestoreMode.MERGE)
        assertEquals(emptySet<String>(), plan.newProjectUuids); assertEquals(setOf("u1"), plan.replacedProjectUuids)
        val replace = BackupCodec.plan(setOf("u1", "u9"), file, RestoreMode.REPLACE)
        assertEquals(setOf("u1"), replace.newProjectUuids); assertEquals(setOf("u1", "u9"), replace.replacedProjectUuids)
    }
}
