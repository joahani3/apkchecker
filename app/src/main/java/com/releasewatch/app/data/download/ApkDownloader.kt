package com.releasewatch.app.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.releasewatch.app.data.network.model.GithubAsset

object ApkDownloader {

    suspend fun download(context: Context, asset: GithubAsset, token: String?) {
        val resolvedUrl = GithubAssetFetcher.resolveFinalUrl(asset.browserDownloadUrl, token)

        val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
            setTitle(asset.name)
            setDescription("ReleaseWatch")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "${asset.name} 다운로드를 시작합니다", Toast.LENGTH_SHORT).show()
    }
}
