package com.releasewatch.app.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.releasewatch.app.data.network.model.GithubAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object ApkDownloader {

    suspend fun download(context: Context, asset: GithubAsset, token: String?) {
        val resolvedUrl = GithubAssetFetcher.resolveFinalUrl(asset.browserDownloadUrl, token)
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

        val failureReason = withContext(Dispatchers.IO) { awaitFailureReason(downloadManager, downloadId) }
        if (failureReason != null) {
            Toast.makeText(
                context,
                "다운로드 실패 (오류 코드 $failureReason: ${describeFailure(failureReason)})",
                Toast.LENGTH_LONG
            ).show()
        }
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

    private suspend fun awaitFailureReason(downloadManager: DownloadManager, downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        while (true) {
            val result = downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val statusCode = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                statusCode to reason
            } ?: return null

            when (result.first) {
                DownloadManager.STATUS_SUCCESSFUL -> return null
                DownloadManager.STATUS_FAILED -> return result.second
            }
            delay(500)
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
