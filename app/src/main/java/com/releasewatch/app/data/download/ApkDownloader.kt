package com.releasewatch.app.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.releasewatch.app.data.network.model.GithubAsset

object ApkDownloader {

    fun download(context: Context, asset: GithubAsset, token: String?) {
        val request = DownloadManager.Request(Uri.parse(asset.browserDownloadUrl)).apply {
            setTitle(asset.name)
            setDescription("ReleaseWatch")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            setMimeType("application/vnd.android.package-archive")
            addRequestHeader("Accept", "application/octet-stream")
            token?.let { addRequestHeader("Authorization", "Bearer $it") }
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "${asset.name} 다운로드를 시작합니다", Toast.LENGTH_SHORT).show()
    }
}
