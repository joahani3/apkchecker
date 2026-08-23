package com.releasewatch.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RepoPackageDao {

    @Query("SELECT packageName FROM repo_package WHERE repoFullName = :repoFullName")
    suspend fun getPackageName(repoFullName: String): String?

    @Query("SELECT * FROM repo_package")
    suspend fun getAll(): List<RepoPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepoPackageEntity)

    @Query("DELETE FROM repo_package")
    suspend fun clearAll()
}
