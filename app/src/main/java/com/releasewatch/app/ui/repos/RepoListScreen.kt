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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.releasewatch.app.data.download.ApkDownloader
import com.releasewatch.app.data.download.DownloadProgress
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RepoListTab { APK, TODAY, ALL }

private fun tabLabel(name: String, count: Int): String =
    if (count > 0) "$name($count)" else name

private fun emptyMessageFor(tab: RepoListTab, hasAnyRepo: Boolean): String {
    if (!hasAnyRepo) return "감시할 저장소가 없습니다."
    return when (tab) {
        RepoListTab.APK -> "새로 설치하거나 업데이트할 저장소가 없습니다."
        RepoListTab.TODAY -> "오늘 소스가 올라온 저장소가 없습니다."
        RepoListTab.ALL -> "감시할 저장소가 없습니다."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onLoggedOut: () -> Unit,
    onOpenBookshelf: () -> Unit
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
    var pendingInstallId by remember { mutableStateOf<Long?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showHiddenRepos by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(RepoListTab.APK) }
    // asset id -> 진행 상황 (null 값 = 준비 중, 크기 미확인)
    val downloadProgress = remember { mutableStateMapOf<Long, DownloadProgress?>() }

    fun startDownload(asset: GithubAsset) {
        if (asset.id in downloadProgress) return
        downloadProgress[asset.id] = null
        coroutineScope.launch {
            try {
                ApkDownloader.download(context, asset, viewModel.authToken()) { progress ->
                    downloadProgress[asset.id] = progress
                }?.let { pendingInstallId = it }
            } finally {
                downloadProgress.remove(asset.id)
            }
        }
    }

    // 첫 화면에 진입할 때마다(앱 실행/복귀, 다른 화면에서 돌아옴) 새로고침
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.refresh()
    }

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
                startDownload(asset)
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
            startDownload(asset)
        }
    }

    // 신규 설치 대상이거나 업데이트가 있는 저장소
    val apkRepos = remember(uiState.repos) {
        uiState.repos.filter {
            it.installStatus == InstallStatus.NOT_INSTALLED || it.installStatus == InstallStatus.UPDATE_AVAILABLE
        }
    }
    // 오늘 소스가 올라온 저장소: 1순위(오늘 apk 빌드 없음) 다음에 2순위(오늘 apk 빌드 있음) 순으로 정렬
    val todayRepos = remember(uiState.repos) {
        uiState.repos.filter { it.pushedToday }.sortedBy { it.releaseToday }
    }
    val displayedRepos = when (selectedTab) {
        RepoListTab.APK -> apkRepos
        RepoListTab.TODAY -> todayRepos
        RepoListTab.ALL -> uiState.repos
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
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("백업") },
                            leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                backupLauncher.launch("releasewatch_backup.txt")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("복구") },
                            leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                restoreLauncher.launch(arrayOf("text/plain"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("숨긴 저장소 관리") },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showHiddenRepos = true
                                viewModel.loadHiddenRepos()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("로그아웃") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.logout(onLoggedOut)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.weight(1f)
                ) {
                    Tab(
                        selected = selectedTab == RepoListTab.APK,
                        onClick = { selectedTab = RepoListTab.APK },
                        text = { Text(tabLabel("APK", apkRepos.size)) }
                    )
                    Tab(
                        selected = selectedTab == RepoListTab.TODAY,
                        onClick = { selectedTab = RepoListTab.TODAY },
                        text = { Text(tabLabel("TODAY", todayRepos.size)) }
                    )
                    Tab(
                        selected = selectedTab == RepoListTab.ALL,
                        onClick = { selectedTab = RepoListTab.ALL },
                        text = { Text("ALL") }
                    )
                }
                TextButton(onClick = onOpenBookshelf) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("책장")
                }
            }

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
                } else if (!uiState.isLoading && displayedRepos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(emptyMessageFor(selectedTab, hasAnyRepo = uiState.repos.isNotEmpty()))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(displayedRepos, key = { it.repo.fullName }) { repoRelease ->
                            RepoCard(
                                repoRelease = repoRelease,
                                isDownloading = repoRelease.release?.apkAsset?.id?.let { it in downloadProgress } == true,
                                downloadProgress = repoRelease.release?.apkAsset?.id?.let { downloadProgress[it] },
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
            text = { Text("${target.repo.fullName}을(를) 관리 목록에서 삭제할까요?\nGitHub의 star/watch 상태는 유지되며, 상단 메뉴의 '숨긴 저장소 관리'에서 다시 추가할 수 있습니다.") },
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

    pendingInstallId?.let { downloadId ->
        AlertDialog(
            onDismissRequest = { pendingInstallId = null },
            title = { Text("다운로드 완료") },
            text = { Text("설치할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    ApkDownloader.install(context, downloadId)
                    pendingInstallId = null
                }) {
                    Text("설치")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstallId = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (showHiddenRepos) {
        AlertDialog(
            onDismissRequest = { showHiddenRepos = false },
            title = { Text("숨긴 저장소 관리") },
            text = {
                if (uiState.hiddenRepos.isEmpty()) {
                    Text("숨긴 저장소가 없습니다.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        uiState.hiddenRepos.forEach { fullName ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fullName, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.unhideRepo(fullName) }) {
                                    Text("복원")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenRepos = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(
    repoRelease: RepoRelease,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
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
        val apkAsset = repoRelease.release?.apkAsset

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = repoRelease.repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            if (repoRelease.pushedToday || repoRelease.installStatus == InstallStatus.NOT_INSTALLED ||
                repoRelease.installStatus == InstallStatus.UPDATE_AVAILABLE
            ) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (repoRelease.pushedToday) {
                    StatusChip(
                        label = "오늘 업데이트",
                        icon = Icons.Filled.NewReleases,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                when (repoRelease.installStatus) {
                    InstallStatus.NOT_INSTALLED -> StatusChip(
                        label = "new",
                        icon = Icons.Filled.GetApp,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = { apkAsset?.let(onDownloadApk) }
                    )
                    InstallStatus.UPDATE_AVAILABLE -> StatusChip(
                        label = "업데이트 필요",
                        icon = Icons.Filled.Update,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { apkAsset?.let(onDownloadApk) }
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
                repoRelease.playVersions?.closedTesting?.let { version ->
                    Text(
                        text = "Play 비공개 테스트: $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                repoRelease.playVersions?.production?.let { version ->
                    Text(
                        text = "Play 프로덕션: $version",
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
                        label = { Text("APK") },
                        enabled = !isDownloading
                    )
                }
            }

            if (isDownloading) {
                DownloadProgressBar(downloadProgress)
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(progress: DownloadProgress?) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        val fraction = progress?.fraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                progress == null -> "다운로드 준비 중…"
                fraction == null -> "다운로드 중… ${formatBytes(progress.downloadedBytes)}"
                else -> "다운로드 중… ${(fraction * 100).toInt()}% " +
                    "(${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)})"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
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
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {}
) {
    SuggestionChip(
        onClick = onClick,
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
