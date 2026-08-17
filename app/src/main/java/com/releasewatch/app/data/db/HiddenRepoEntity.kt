package com.releasewatch.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_repo")
data class HiddenRepoEntity(
    @PrimaryKey val repoFullName: String
)
