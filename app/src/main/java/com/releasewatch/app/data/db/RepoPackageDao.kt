package com.releasewatch.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RepoPackageDao {

    @Query("SELECT packageName FROM repo_package WHERE repoFullName = :repoFullName")
    suspend fun getPackageName(repoFullName: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepoPackageEntity)
}
