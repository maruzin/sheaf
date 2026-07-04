package com.sheaf.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SheafDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
