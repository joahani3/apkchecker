package com.releasewatch.app.data.db

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReleaseStateDao {

    @Query("SELECT * FROM release_state")
    suspend fun getAll(): List<ReleaseStateEntity>

    @Query("SELECT * FROM release_state WHERE repoFullName = :repoFullName")
    suspend fun getByRepo(repoFullName: String): ReleaseStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReleaseStateEntity)

    @Query("DELETE FROM release_state")
    suspend fun clearAll()
}
