package com.riodev.kernelperf.data.model

import android.content.Context
import androidx.room.*

@Database(entities = [AppProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appProfileDao(): AppProfileDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kernelperf_db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}
