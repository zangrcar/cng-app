package com.zangrcar.cngitaly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StationEntity::class, CngPriceEntity::class, DatasetMetaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CngDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

    companion object {
        @Volatile private var instance: CngDatabase? = null

        fun getInstance(context: Context): CngDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CngDatabase::class.java,
                    "cng-italy.db"
                ).build().also { instance = it }
            }
    }
}
