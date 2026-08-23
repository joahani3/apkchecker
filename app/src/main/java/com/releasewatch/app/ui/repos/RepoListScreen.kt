package com.releasewatch.app.ui.repos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.releasewatch.app.data.download.ApkDownloader
import com.releasewatch.app.data.install.InstalledAppChecker
import com.releasewatch.app.data.network.model.GithubAsset
import com.releasewatch.app.data.network.model.apkAsset
import com.releasewatch.app.data.repository.InstallStatus
import com.releasewatch.app.data.repository.RepoRelease
import com.releasewatch.app.data.repository.RepoSource
import com.releasewatch.app.ui.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onLoggedOut: () -> Unit
) {
    val viewModel: RepoListViewModel = viewModelFactory { RepoListViewModel(it.gitHubRepository, it.backupManager) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    val listState = rememberLazyListState()

    val appVersionName = remember {
        InstalledAppChecker.getInstalledPackageInfo(context, context.packageName)?.versionName
    }

    var pendingDownload by remember { mutableStateOf<GithubAsset?>(null) }
    var repoPendingRemoval by remember { mutableStateOf<RepoRelease?>(null) }
    var wasLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (wasLoading && !uiState.isLoading) {
            listState.scrollToItem(0)
        }
        wasLoading = uiState.isLoading
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingDownload?.let { asset ->
                coroutineScope.launch { ApkDownloader.download(context, asset, viewModel.authToken()) }
            }
        }
        pendingDownload = null
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val text = viewModel.createBackupText()
                val wrote = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    }.isSuccess
                }
                val message = if (wrote) "백업을 저장했습니다" else "백업 저장에 실패했습니다"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text == null) {
                    Toast.makeText(context, "백업 파일을 읽을 수 없습니다", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.restoreBackup(text)
                        .onSuccess { Toast.makeText(context, "복구했습니다", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "복구 실패: 백업 파일 형식을 확인해주세요", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun requestDownload(asset: GithubAsset) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            pendingDownload = asset
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            coroutineScope.launch { ApkDownloader.download(context, asset, viewModel.authToken()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.username?.let { "$it 님의 저장소" } ?: "ReleaseWatch")
                },
                actions = {
                    appVersionName?.let { version ->
                        Text(
                            text = "v$version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = { backupLauncher.launch("releasewatch_backup.txt") }) {
                        Icon(Icons.Filled.Save, contentDescription = "백업")
                    }
                    IconButton(onClick = { restoreLauncher.launch(arrayOf("text/plain")) }) {
                        Icon(Icons.Filled.Restore, contentDescription = "복구")
                    }
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "로그아웃")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.scopeWarning?.let { warning ->
                ScopeWarningBanner(message = warning, onDismiss = { viewModel.dismissScopeWarning() })
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
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
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(uiState.repos, key = { it.repo.fullName }) { repoRelease ->
                            RepoCard(
                                repoRelease = repoRelease,
                                onOpenRelease = {
                                    val url = repoRelease.release?.htmlUrl ?: repoRelease.repo.htmlUrl
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                onDownloadApk = { asset -> requestDownload(asset) },
                                onLongPressRemove = { repoPendingRemoval = repoRelease }
                            )
                        }
                    }
                }
            }
        }
    }

    repoPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { repoPendingRemoval = null },
            title = { Text("관리 목록에서 삭제") },
            text = { Text("${target.repo.fullName}을(를) 관리 목록에서 삭제할까요?\nGitHub의 star/watch 상태는 유지되며, 다시 보려면 앱에서 재추가할 방법이 없으니 주의해주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeRepo(target)
                    repoPendingRemoval = null
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoPendingRemoval = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(
    repoRelease: RepoRelease,
    onOpenRelease: () -> Unit,
    onDownloadApk: (GithubAsset) -> Unit,
    onLongPressRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onOpenRelease,
                onLongClick = onLongPressRemove,
                onClickLabel = "릴리즈 열기",
                onLongClickLabel = "관리 목록에서 삭제"
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repoRelease.repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                when (repoRelease.installStatus) {
                    InstallStatus.NOT_INSTALLED -> StatusChip(
                        label = "new",
                        icon = Icons.Filled.GetApp,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                    InstallStatus.UPDATE_AVAILABLE -> StatusChip(
                        label = "업데이트 필요",
                        icon = Icons.Filled.Update,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    else -> {}
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
                repoRelease.installedVersionName?.let { installedVersion ->
                    Text(
                        text = "설치된 버전: $installedVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                }
            }
        }
    }
}

@Composable
private fun ScopeWarningBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color
) {
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        icon = {
            Icon(icon, contentDescription = null, modifier = Modifier.width(16.dp))
        },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = containerColor)
    )
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
