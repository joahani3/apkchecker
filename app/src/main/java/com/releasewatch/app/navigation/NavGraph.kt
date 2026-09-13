package com.releasewatch.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.releasewatch.app.ReleaseWatchApp
import com.releasewatch.app.ui.bookshelf.BookshelfScreen
import com.releasewatch.app.ui.login.LoginScreen
import com.releasewatch.app.ui.repos.RepoListScreen

private object Routes {
    const val LOGIN = "login"
    const val REPOS = "repos"
    const val BOOKSHELF = "bookshelf"
}

@Composable
fun ReleaseWatchNavGraph() {
    val navController: NavHostController = rememberNavController()
    val container = (LocalContext.current.applicationContext as ReleaseWatchApp).container
    val startDestination = if (container.gitHubRepository.isLoggedIn()) Routes.REPOS else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.REPOS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.REPOS) {
            RepoListScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REPOS) { inclusive = true }
                    }
                },
                onOpenBookshelf = { navController.navigate(Routes.BOOKSHELF) }
            )
        }
        composable(Routes.BOOKSHELF) {
            BookshelfScreen(onBack = { navController.popBackStack() })
        }
    }
}
