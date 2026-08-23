package com.releasewatch.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HiddenRepoDao {

    @Query("SELECT repoFullName FROM hidden_repo")
    suspend fun getAllFullNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenRepoEntity)

    @Query("DELETE FROM hidden_repo WHERE repoFullName = :repoFullName")
    suspend fun unhide(repoFullName: String)

    @Query("DELETE FROM hidden_repo")
    suspend fun clearAll()
}
