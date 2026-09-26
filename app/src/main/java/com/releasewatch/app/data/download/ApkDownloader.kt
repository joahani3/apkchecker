package com.releasewatch.app.data.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.releasewatch.app.data.network.model.GithubAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Bytes received so far; [totalBytes] is -1 until the server reports a size. */
data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

object ApkDownloader {

    /**
     * Returns the completed download's id, or null if the download failed.
     * [onProgress] is called on the caller's dispatcher while the download runs.
     */
    suspend fun download(
        context: Context,
        asset: GithubAsset,
        token: String?,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Long? {
        val resolvedUrl = GithubAssetFetcher.resolveFinalUrl(asset, token)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Every release's APK asset is named "app-debug.apk" (see release-apk.yml), so without
        // this, downloading a second release fails immediately with ERROR_FILE_ALREADY_EXISTS
        // because DownloadManager refuses to overwrite the file this app already saved for the
        // previous one.
        removeExistingDownloads(downloadManager, asset.name)

        val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
            setTitle(asset.name)
            setDescription("ReleaseWatch")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)
        Toast.makeText(context, "${asset.name} 다운로드를 시작합니다", Toast.LENGTH_SHORT).show()

        val failureReason = awaitFailureReason(downloadManager, downloadId, onProgress)
        if (failureReason != null) {
            Toast.makeText(
                context,
                "다운로드 실패 (오류 코드 $failureReason: ${describeFailure(failureReason)})",
                Toast.LENGTH_LONG
            ).show()
            return null
        }
        return downloadId
    }

    /** Launches the package installer for a completed download, prompting for the install-source permission first if needed. */
    fun install(context: Context, downloadId: Long) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "설치를 위해 '알 수 없는 앱 설치' 권한을 허용해주세요", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        val mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun removeExistingDownloads(downloadManager: DownloadManager, title: String) {
        val idsToRemove = mutableListOf<Long>()
        downloadManager.query(DownloadManager.Query())?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            while (cursor.moveToNext()) {
                if (cursor.getString(titleColumn) == title) {
                    idsToRemove.add(cursor.getLong(idColumn))
                }
            }
        }
        if (idsToRemove.isNotEmpty()) {
            downloadManager.remove(*idsToRemove.toLongArray())
        }
    }

    private class DownloadSnapshot(val status: Int, val reason: Int, val progress: DownloadProgress)

    private suspend fun awaitFailureReason(
        downloadManager: DownloadManager,
        downloadId: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                downloadManager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    DownloadSnapshot(
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                        progress = DownloadProgress(
                            downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        )
                    )
                }
            } ?: return null

            onProgress(snapshot.progress)
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> return null
                DownloadManager.STATUS_FAILED -> return snapshot.reason
            }
            delay(300)
        }
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "다운로드를 재개할 수 없음"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "저장 장치를 찾을 수 없음"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "동일한 이름의 파일이 이미 존재함"
        DownloadManager.ERROR_FILE_ERROR -> "파일 저장 오류"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP 데이터 오류"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "저장 공간 부족"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "리다이렉트가 너무 많음"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "처리할 수 없는 HTTP 응답 코드"
        DownloadManager.ERROR_UNKNOWN -> "알 수 없는 오류"
        in 400..599 -> "HTTP $reason"
        else -> "알 수 없음"
    }
}
