package com.riodev.kernelperf.data.model

import android.content.Context
import androidx.room.*

@Database(entities = [AppProfile::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppProfileDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kp_db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
