package com.mohdshayan.rowmark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1 until the app ships; schema export is off because there is nothing to migrate from yet.
 * Turn it on and write a Migration before version 2.
 */
@Database(
    entities = [
        Project::class, Counter::class, CountEvent::class, Session::class, Schedule::class,
        Pattern::class, Markup::class, Yarn::class, Tool::class, ProjectYarn::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun counterDao(): CounterDao
    abstract fun countEventDao(): CountEventDao
    abstract fun sessionDao(): SessionDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun patternDao(): PatternDao
    abstract fun markupDao(): MarkupDao
    abstract fun yarnDao(): YarnDao
    abstract fun toolDao(): ToolDao
    abstract fun projectYarnDao(): ProjectYarnDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "rowmark.db")
                    .build().also { instance = it }
            }
    }
}
