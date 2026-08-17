package com.releasewatch.app.ui.repos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.releasewatch.app.data.download.ApkDownloader
import com.releasewatch.app.data.network.model.GithubAsset
import com.releasewatch.app.data.network.model.apkAsset
import com.releasewatch.app.data.repository.RepoRelease
import com.releasewatch.app.data.repository.RepoSource
import com.releasewatch.app.ui.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onLoggedOut: () -> Unit
) {
    val viewModel: RepoListViewModel = viewModelFactory { RepoListViewModel(it.gitHubRepository) }
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var pendingDownload by remember { mutableStateOf<GithubAsset?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingDownload?.let { ApkDownloader.download(context, it, viewModel.authToken()) }
        }
        pendingDownload = null
    }

    fun requestDownload(asset: GithubAsset) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            pendingDownload = asset
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            ApkDownloader.download(context, asset, viewModel.authToken())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.username?.let { "$it 님의 저장소" } ?: "ReleaseWatch")
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "로그아웃")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.errorMessage != null && uiState.repos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage)
                }
            } else if (!uiState.isLoading && uiState.repos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("감시할 저장소가 없습니다.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.repos, key = { it.repo.fullName }) { repoRelease ->
                        RepoCard(
                            repoRelease = repoRelease,
                            onOpenRelease = {
                                val url = repoRelease.release?.htmlUrl ?: repoRelease.repo.htmlUrl
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            onDownloadApk = { asset -> requestDownload(asset) },
                            onMarkComplete = { viewModel.markComplete(repoRelease) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(
    repoRelease: RepoRelease,
    onOpenRelease: () -> Unit,
    onDownloadApk: (GithubAsset) -> Unit,
    onMarkComplete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpenRelease)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repoRelease.repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (repoRelease.isNew) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("NEW") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val release = repoRelease.release
            if (release != null) {
                Text(
                    text = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatDate(release.publishedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "릴리즈 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (RepoSource.OWNED in repoRelease.sources) {
                        SourceTag(icon = Icons.AutoMirrored.Filled.OpenInNew, label = "내 저장소")
                    }
                    if (RepoSource.STARRED in repoRelease.sources) {
                        SourceTag(icon = Icons.Filled.Star, label = "Star")
                    }
                    if (RepoSource.WATCHED in repoRelease.sources) {
                        SourceTag(icon = Icons.Filled.Visibility, label = "Watch")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val apkAsset = release?.apkAsset
                    if (apkAsset != null) {
                        AssistChip(
                            onClick = { onDownloadApk(apkAsset) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.width(16.dp)
                                )
                            },
                            label = { Text("APK") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = onMarkComplete, enabled = repoRelease.isNew) {
                        Icon(
                            imageVector = if (repoRelease.isNew) {
                                Icons.Filled.RadioButtonUnchecked
                            } else {
                                Icons.Filled.CheckCircle
                            },
                            contentDescription = if (repoRelease.isNew) "완료로 표시" else "완료됨",
                            tint = if (repoRelease.isNew) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (e: Exception) {
        ""
    }
}
