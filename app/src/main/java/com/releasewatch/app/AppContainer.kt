package com.releasewatch.app

import android.content.Context
import com.releasewatch.app.data.auth.TokenStore
import com.releasewatch.app.data.backup.BackupManager
import com.releasewatch.app.data.db.AppDatabase
import com.releasewatch.app.data.network.NetworkModule
import com.releasewatch.app.data.playconsole.PlayConsoleRepository
import com.releasewatch.app.data.repository.GitHubRepository

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(context)

    private val api = NetworkModule.createApi(tokenStore)

    private val db = AppDatabase.getInstance(context)

    private val playConsoleRepository = PlayConsoleRepository(
        gitHubApi = api,
        tokenStore = tokenStore
    )

    val gitHubRepository = GitHubRepository(
        context = appContext,
        api = api,
        tokenStore = tokenStore,
        dao = db.releaseStateDao(),
        hiddenRepoDao = db.hiddenRepoDao(),
        repoPackageDao = db.repoPackageDao(),
        playConsoleRepository = playConsoleRepository
    )

    val backupManager = BackupManager(
        tokenStore = tokenStore,
        hiddenRepoDao = db.hiddenRepoDao(),
        releaseStateDao = db.releaseStateDao(),
        repoPackageDao = db.repoPackageDao()
    )
}
