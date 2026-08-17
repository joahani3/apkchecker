package com.releasewatch.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "release_state")
data class ReleaseStateEntity(
    @PrimaryKey val repoFullName: String,
    val lastSeenReleaseId: Long?,
    val lastSeenTag: String?
)
