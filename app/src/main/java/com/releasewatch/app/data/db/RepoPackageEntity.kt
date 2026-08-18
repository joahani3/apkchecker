package com.releasewatch.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repo_package")
data class RepoPackageEntity(
    @PrimaryKey val repoFullName: String,
    val packageName: String
)
