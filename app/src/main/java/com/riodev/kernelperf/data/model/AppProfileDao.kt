package com.riodev.kernelperf.data.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {

    @Query("SELECT * FROM app_profiles ORDER BY appName ASC")
    fun getAllProfiles(): Flow<List<AppProfile>>

    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getProfile(packageName: String): AppProfile?

    @Query("SELECT * FROM app_profiles WHERE isEnabled = 1")
    suspend fun getEnabledProfiles(): List<AppProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: AppProfile)

    @Update
    suspend fun updateProfile(profile: AppProfile)

    @Delete
    suspend fun deleteProfile(profile: AppProfile)

    @Query("DELETE FROM app_profiles WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT COUNT(*) FROM app_profiles")
    fun getProfileCount(): Flow<Int>
}
